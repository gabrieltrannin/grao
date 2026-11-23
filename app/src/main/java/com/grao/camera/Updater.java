package com.grao.camera;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Autoatualização: lê o versao.json publicado junto com cada versão,
 * compara com a versão instalada, baixa o APK novo e chama o instalador do Android.
 *
 * Formato do versao.json:
 * {"versionCode": 7, "versionName": "1.0.7", "apkUrl": "https://.../grao.apk", "notes": "O que mudou"}
 */
class Updater {

    private final MainActivity act;
    private JSONObject pending;          // atualização aguardando a permissão de instalar apps
    private boolean busy;

    Updater(MainActivity act) {
        this.act = act;
    }

    void check(boolean manual) {
        String url = BuildConfig.UPDATE_URL;
        if (url == null || url.isEmpty()) {
            if (manual) act.toast("Endereço de atualização não configurado nesta versão");
            return;
        }
        if (busy) return;
        busy = true;
        new Thread(() -> {
            try {
                JSONObject info = new JSONObject(new String(get(url), "UTF-8"));
                int remote = info.getInt("versionCode");
                if (remote > BuildConfig.VERSION_CODE) {
                    act.runOnUiThread(() -> offer(info));
                } else if (manual) {
                    act.toast("Você já está na versão mais recente (" + BuildConfig.VERSION_NAME + ")");
                }
            } catch (Exception e) {
                if (manual) act.toast("Não consegui procurar atualização. Confira a internet.");
            } finally {
                busy = false;
            }
        }).start();
    }

    private void offer(JSONObject info) {
        if (act.isFinishing()) return;
        String notes = info.optString("notes", "");
        new AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Atualização disponível: " + info.optString("versionName"))
                .setMessage((notes.isEmpty() ? "" : notes + "\n\n") + "Você tem a versão " + BuildConfig.VERSION_NAME + ".")
                .setPositiveButton("Atualizar", (d, w) -> start(info))
                .setNegativeButton("Agora não", null)
                .show();
    }

    private void start(JSONObject info) {
        if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
            pending = info;
            new AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Permitir atualizações")
                    .setMessage("Na próxima tela, ative \"Permitir desta fonte\" para o Grão poder instalar as próprias atualizações. Depois é só voltar.")
                    .setPositiveButton("Abrir", (d, w) -> act.startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + act.getPackageName()))))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        download(info);
    }

    /** Ao voltar das configurações, continua a atualização se a permissão foi dada. */
    void onResume() {
        if (pending != null && act.getPackageManager().canRequestPackageInstalls()) {
            JSONObject info = pending;
            pending = null;
            download(info);
        }
    }

    private void download(JSONObject info) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * act.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        ProgressBar bar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        TextView label = new TextView(act);
        label.setText("0%");
        box.addView(bar);
        box.addView(label);
        AlertDialog dlg = new AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Baixando " + info.optString("versionName"))
                .setView(box)
                .setCancelable(false)
                .show();

        new Thread(() -> {
            try {
                PackageInstaller pi = act.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(act.getPackageName());
                if (Build.VERSION.SDK_INT >= 31) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                }
                int id = pi.createSession(params);
                try (PackageInstaller.Session session = pi.openSession(id)) {
                    HttpURLConnection c = open(info.getString("apkUrl"));
                    long total = c.getContentLengthLong();
                    try (InputStream in = c.getInputStream();
                         OutputStream out = session.openWrite("grao.apk", 0, total > 0 ? total : -1)) {
                        byte[] buf = new byte[64 * 1024];
                        long done = 0;
                        int n, lastPct = -1;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            done += n;
                            if (total > 0) {
                                int pct = (int) (done * 100 / total);
                                if (pct != lastPct) {
                                    lastPct = pct;
                                    act.runOnUiThread(() -> { bar.setProgress(pct); label.setText(pct + "%"); });
                                }
                            }
                        }
                        session.fsync(out);
                    }
                    Intent i = new Intent(act, InstallReceiver.class);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT
                            | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent pIntent = PendingIntent.getBroadcast(act, id, i, flags);
                    session.commit(pIntent.getIntentSender());
                }
                act.runOnUiThread(dlg::dismiss);
            } catch (Exception e) {
                act.runOnUiThread(() -> {
                    dlg.dismiss();
                    act.toast("A atualização falhou. Tente de novo mais tarde.");
                });
            }
        }).start();
    }

    private static HttpURLConnection open(String url) throws Exception {
        // Segue redirecionamentos manualmente (o GitHub redireciona os downloads).
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Grao-Updater");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                url = new URL(new URL(url), c.getHeaderField("Location")).toString();
                c.disconnect();
                continue;
            }
            if (code != 200) throw new Exception("HTTP " + code);
            return c;
        }
        throw new Exception("Redirecionamentos demais");
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
