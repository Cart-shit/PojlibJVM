package pojlib.util;

import android.content.res.AssetManager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import pojlib.api.API_V1;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Objects;

import static pojlib.util.FileUtil.read;
import static pojlib.util.GsonUtils.GLOBAL_GSON;

public class DownloadUtils {

    public static AssetManager assetManager;


    private static void download(URL url, OutputStream os) throws IOException {
        final int MAX_RETRIES = 3;
        int attempts = 0;

        while (true) {
            try {
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setDoInput(true);
                conn.connect();
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream is = new StreamDL(conn.getInputStream())) {
                        String[] segments = url.getPath().split("/");
                        API_V1.currentDownload = segments[segments.length - 1];

                        ((StreamDL)is).addListener((b, count) -> {

                            if (b == -1) {
                                API_V1.downloadStatus = 0;
                                API_V1.currentDownload = null;
                            } else {
                                API_V1.downloadStatus = count * 0.000001;
                            }

                        });

                        IOUtils.copy(is, os);
                    }
                    return;
                }

            } catch (IOException e) {
                if (++attempts >= MAX_RETRIES || e instanceof SSLException) {
                    throw new IOException("Unable to download from " + url, e);
                }
            }
        }
    }

    public static void downloadFile(String url, File out) throws IOException {
        Objects.requireNonNull(out.getParentFile()).mkdirs();
        File tempOut = File.createTempFile(out.getName(), ".part", out.getParentFile());
        BufferedOutputStream bos = null;
        try {
            OutputStream bos2 = new BufferedOutputStream(Files.newOutputStream(tempOut.toPath()));
            try {
                download(new URL(url), bos2);
                tempOut.renameTo(out);
                bos2.close();
                if (tempOut.exists()) tempOut.delete();

            } catch (IOException th2) {
                if (tempOut.exists()) tempOut.delete();
                throw th2;
            }
        } catch (IOException th3) {
            if (tempOut.exists()) tempOut.delete();
            throw th3;
        }
    }
    public static boolean compareSHA1(File f, String sourceSHA) {
        try {
            String sha1_dst;
            try (InputStream is = Files.newInputStream(f.toPath())) {
                sha1_dst = new String(Hex.encodeHex(DigestUtils.sha1(is)));
            }
            if (sourceSHA != null) return sha1_dst.equalsIgnoreCase(sourceSHA);
            else return true; // fake match

        }catch (IOException e) {
            System.out.println("Fake-matching a hash due to a read error: " + e);
            return true;
        }
    }

    public static ArrayList<String> getCompatibleVersions(String tag) {
        ArrayList<String> versions = new ArrayList<>();
        try {
            InputStream stream = assetManager.open("jsons/modmanager.json");
            JsonObject versionsJson = GLOBAL_GSON.fromJson(read(stream), JsonObject.class);

            for (JsonElement version : versionsJson.get("compatible_versions").getAsJsonObject().getAsJsonArray(tag)) {
                versions.add(version.getAsString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return versions;
    }
}