package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public final class Live2DModelImporter {
    public static final String PREF_MODEL_LABEL = "live2d_model_label";
    public static final String PREF_MOTION_COUNT = "live2d_motion_count";
    public static final String PREF_EXPRESSION_COUNT = "live2d_expression_count";

    private Live2DModelImporter() {
    }

    public static class Result {
        public final boolean success;
        public final String modelPath;
        public final String label;
        public final String message;
        public final int motionCount;
        public final int expressionCount;

        Result(boolean success, String modelPath, String label, String message) {
            this(success, modelPath, label, message, 0, 0);
        }

        Result(boolean success, String modelPath, String label, String message, int motionCount) {
            this(success, modelPath, label, message, motionCount, 0);
        }

        Result(boolean success, String modelPath, String label, String message, int motionCount, int expressionCount) {
            this.success = success;
            this.modelPath = modelPath;
            this.label = label;
            this.message = message;
            this.motionCount = motionCount;
            this.expressionCount = expressionCount;
        }
    }

    public static Result importFromTreeUri(Context context, Uri treeUri) {
        if (context == null || treeUri == null) {
            return new Result(false, "", "", "没有选择 Live2D 模型文件夹");
        }

        File outRoot = new File(context.getFilesDir(), "live2d/selected_model");
        try {
            deleteRecursively(outRoot);
            if (!outRoot.mkdirs() && !outRoot.exists()) {
                return new Result(false, "", "", "无法创建模型缓存目录");
            }

            String rootDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId);
            DocumentMeta rootMeta = queryMeta(context, rootDocUri);
            String label = rootMeta.name != null && rootMeta.name.length() > 0 ? rootMeta.name : "Live2D模型";

            copyDocumentTree(context, treeUri, rootDocUri, outRoot);

            File modelFile = findModelFile(outRoot);
            if (modelFile == null) {
                return new Result(false, "", label, "没有在文件夹里找到 model3.json 或 model.json");
            }

            int motionCount = countMotionFiles(outRoot);
            int expressionCount = countExpressionFiles(outRoot);
            boolean installedDefaultMotions = false;
            boolean installedDefaultExpressions = false;

            if (motionCount <= 0) {
                motionCount = installDefaultMotionsIfPossible(modelFile);
                installedDefaultMotions = motionCount > 0;
            }

            if (expressionCount <= 0) {
                expressionCount = installDefaultExpressionsIfPossible(modelFile);
                installedDefaultExpressions = expressionCount > 0;
            }

            String message = "已导入：" + label;
            if (motionCount > 0) {
                message += installedDefaultMotions
                        ? "，未发现自带动作，已加入 " + motionCount + " 个通用动作"
                        : "，读取到 " + motionCount + " 个动作文件";
            } else {
                message += "，未发现动作文件";
            }

            if (expressionCount > 0) {
                message += installedDefaultExpressions
                        ? "，已加入 " + expressionCount + " 个通用表情"
                        : "，读取到 " + expressionCount + " 个表情文件";
            }

            return new Result(true, modelFile.getAbsolutePath(), label, message, motionCount, expressionCount);
        } catch (Throwable t) {
            return new Result(false, "", "", "导入失败：" + t.getMessage());
        }
    }

    private static void copyDocumentTree(Context context, Uri treeUri, Uri docUri, File outDir) throws Exception {
        DocumentMeta meta = queryMeta(context, docUri);
        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(meta.mimeType)) {
            if (!outDir.exists()) {
                outDir.mkdirs();
            }
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri,
                    DocumentsContract.getDocumentId(docUri)
            );

            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(
                        childrenUri,
                        new String[]{
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                DocumentsContract.Document.COLUMN_MIME_TYPE
                        },
                        null,
                        null,
                        null
                );

                if (cursor == null) {
                    return;
                }

                while (cursor.moveToNext()) {
                    String childId = cursor.getString(0);
                    String childName = sanitizeFileName(cursor.getString(1));
                    String childMime = cursor.getString(2);

                    Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(childMime)) {
                        File childDir = new File(outDir, childName);
                        copyDocumentTree(context, treeUri, childUri, childDir);
                    } else {
                        copyFile(context, childUri, new File(outDir, childName));
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else {
            File outFile = new File(outDir, sanitizeFileName(meta.name));
            copyFile(context, docUri, outFile);
        }
    }

    private static void copyFile(Context context, Uri src, File outFile) throws Exception {
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = context.getContentResolver().openInputStream(src);
            if (input == null) {
                return;
            }
            output = new FileOutputStream(outFile);
            byte[] buffer = new byte[32 * 1024];
            int len;
            while ((len = input.read(buffer)) >= 0) {
                output.write(buffer, 0, len);
            }
            output.flush();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static int installDefaultMotionsIfPossible(File modelFile) {
        if (modelFile == null || !modelFile.exists()) {
            return 0;
        }

        String name = modelFile.getName().toLowerCase();
        // 通用 motion3.json 只适配 Cubism 3/4 的 .model3.json。
        // Cubism 2 的 model.json / mtn 结构不在这里强行注入，避免把模型配置改坏。
        if (!name.endsWith(".model3.json")) {
            return 0;
        }

        try {
            File modelDir = modelFile.getParentFile();
            if (modelDir == null) {
                return 0;
            }

            File motionDir = new File(modelDir, "motions_default");
            if (!motionDir.exists() && !motionDir.mkdirs()) {
                return 0;
            }

            writeText(new File(motionDir, "miku_default_idle.motion3.json"), defaultIdleMotionJson());
            writeText(new File(motionDir, "miku_default_blink.motion3.json"), defaultBlinkMotionJson());
            writeText(new File(motionDir, "miku_default_smile.motion3.json"), defaultSmileMotionJson());
            writeText(new File(motionDir, "miku_default_nod.motion3.json"), defaultNodMotionJson());

            JSONObject root = new JSONObject(readText(modelFile));
            JSONObject refs = root.optJSONObject("FileReferences");
            if (refs == null) {
                refs = new JSONObject();
                root.put("FileReferences", refs);
            }

            JSONObject motions = refs.optJSONObject("Motions");
            if (motions == null) {
                motions = new JSONObject();
                refs.put("Motions", motions);
            }

            JSONArray idle = new JSONArray();
            idle.put(new JSONObject().put("File", "motions_default/miku_default_idle.motion3.json"));
            idle.put(new JSONObject().put("File", "motions_default/miku_default_blink.motion3.json"));
            idle.put(new JSONObject().put("File", "motions_default/miku_default_nod.motion3.json"));
            idle.put(new JSONObject().put("File", "motions_default/miku_default_smile.motion3.json"));

            JSONArray tapBody = new JSONArray();
            tapBody.put(new JSONObject().put("File", "motions_default/miku_default_smile.motion3.json"));
            tapBody.put(new JSONObject().put("File", "motions_default/miku_default_nod.motion3.json"));
            tapBody.put(new JSONObject().put("File", "motions_default/miku_default_blink.motion3.json"));

            motions.put("Idle", idle);
            motions.put("TapBody", tapBody);

            writeText(modelFile, root.toString(2));
            return 4;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String readText(File file) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
        }
        br.close();
        return sb.toString();
    }

    private static void writeText(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        writer.write(text);
        writer.flush();
        writer.close();
    }

    private static String curve(String id, double[][] points) {
        StringBuilder segments = new StringBuilder();
        segments.append("[");
        for (int i = 0; i < points.length; i++) {
            if (i == 0) {
                segments.append(format(points[i][0])).append(",").append(format(points[i][1]));
            } else {
                segments.append(",0,").append(format(points[i][0])).append(",").append(format(points[i][1]));
            }
        }
        segments.append("]");

        return "{\"Target\":\"Parameter\",\"Id\":\"" + id + "\",\"FadeInTime\":0.5,\"FadeOutTime\":0.5,\"Segments\":" + segments.toString() + "}";
    }

    private static String motionJson(double duration, boolean loop, String[] curves) {
        int segmentCount = 0;
        int pointCount = 0;
        for (String curve : curves) {
            int c = countOccurrences(curve, ",0,");
            segmentCount += c;
            pointCount += c + 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("\"Version\":3,\n");
        sb.append("\"Meta\":{\n");
        sb.append("\"Duration\":").append(format(duration)).append(",\n");
        sb.append("\"Fps\":30,\n");
        sb.append("\"Loop\":").append(loop ? "true" : "false").append(",\n");
        sb.append("\"FadeInTime\":0.5,\n");
        sb.append("\"FadeOutTime\":0.5,\n");
        sb.append("\"CurveCount\":").append(curves.length).append(",\n");
        sb.append("\"TotalSegmentCount\":").append(Math.max(1, segmentCount)).append(",\n");
        sb.append("\"TotalPointCount\":").append(Math.max(2, pointCount)).append("\n");
        sb.append("},\n");
        sb.append("\"Curves\":[\n");
        for (int i = 0; i < curves.length; i++) {
            if (i > 0) sb.append(",\n");
            sb.append(curves[i]);
        }
        sb.append("\n]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static int countOccurrences(String text, String target) {
        if (text == null || target == null || target.length() == 0) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while (true) {
            index = text.indexOf(target, index);
            if (index < 0) {
                return count;
            }
            count++;
            index += target.length();
        }
    }

    private static String format(double v) {
        if (Math.abs(v - Math.round(v)) < 0.00001) {
            return String.valueOf((long) Math.round(v));
        }
        return String.valueOf(v);
    }

    private static String defaultIdleMotionJson() {
        return motionJson(5.0, false, new String[]{
                curve("ParamAngleX", new double[][]{{0, 0}, {1.25, 7}, {2.5, 0}, {3.75, -7}, {5.0, 0}}),
                curve("ParamAngleY", new double[][]{{0, 0}, {1.25, -3}, {2.5, 0}, {3.75, 3}, {5.0, 0}}),
                curve("ParamAngleZ", new double[][]{{0, 0}, {1.25, -2}, {2.5, 0}, {3.75, 2}, {5.0, 0}}),
                curve("ParamBodyAngleX", new double[][]{{0, 0}, {2.5, 3}, {5.0, 0}}),
                curve("ParamEyeLOpen", new double[][]{{0, 1}, {2.0, 1}, {2.08, 0}, {2.18, 1}, {5.0, 1}}),
                curve("ParamEyeROpen", new double[][]{{0, 1}, {2.0, 1}, {2.08, 0}, {2.18, 1}, {5.0, 1}}),
                curve("ParamMouthOpenY", new double[][]{{0, 0.1}, {2.5, 0.22}, {5.0, 0.1}})
        });
    }

    private static String defaultBlinkMotionJson() {
        return motionJson(1.8, false, new String[]{
                curve("ParamEyeLOpen", new double[][]{{0, 1}, {0.35, 1}, {0.42, 0}, {0.54, 1}, {1.8, 1}}),
                curve("ParamEyeROpen", new double[][]{{0, 1}, {0.35, 1}, {0.42, 0}, {0.54, 1}, {1.8, 1}}),
                curve("ParamAngleX", new double[][]{{0, 0}, {0.9, 2}, {1.8, 0}}),
                curve("ParamMouthOpenY", new double[][]{{0, 0.06}, {0.9, 0.12}, {1.8, 0.06}})
        });
    }

    private static String defaultSmileMotionJson() {
        return motionJson(2.8, false, new String[]{
                curve("ParamMouthForm", new double[][]{{0, 0}, {0.45, 1}, {2.1, 1}, {2.8, 0}}),
                curve("ParamMouthOpenY", new double[][]{{0, 0.05}, {0.45, 0.35}, {2.1, 0.25}, {2.8, 0.05}}),
                curve("ParamEyeSmile", new double[][]{{0, 0}, {0.45, 1}, {2.1, 1}, {2.8, 0}}),
                curve("ParamAngleX", new double[][]{{0, 0}, {0.75, -5}, {1.6, 5}, {2.8, 0}}),
                curve("ParamAngleY", new double[][]{{0, 0}, {0.75, -2}, {1.6, 2}, {2.8, 0}})
        });
    }

    private static String defaultNodMotionJson() {
        return motionJson(3.2, false, new String[]{
                curve("ParamAngleY", new double[][]{{0, 0}, {0.45, -8}, {0.9, 5}, {1.35, -6}, {2.2, 0}, {3.2, 0}}),
                curve("ParamAngleX", new double[][]{{0, 0}, {0.8, 4}, {1.6, -4}, {2.4, 3}, {3.2, 0}}),
                curve("ParamBodyAngleX", new double[][]{{0, 0}, {0.8, 2}, {1.6, -2}, {3.2, 0}}),
                curve("ParamEyeLOpen", new double[][]{{0, 1}, {1.1, 1}, {1.18, 0}, {1.3, 1}, {3.2, 1}}),
                curve("ParamEyeROpen", new double[][]{{0, 1}, {1.1, 1}, {1.18, 0}, {1.3, 1}, {3.2, 1}})
        });
    }

    private static int installDefaultExpressionsIfPossible(File modelFile) {
        if (modelFile == null || !modelFile.exists()) {
            return 0;
        }

        String name = modelFile.getName().toLowerCase();
        if (!name.endsWith(".model3.json")) {
            return 0;
        }

        try {
            File modelDir = modelFile.getParentFile();
            if (modelDir == null) {
                return 0;
            }

            File expressionDir = new File(modelDir, "expressions_default");
            if (!expressionDir.exists() && !expressionDir.mkdirs()) {
                return 0;
            }

            writeText(new File(expressionDir, "miku_default_smile.exp3.json"), defaultSmileExpressionJson());
            writeText(new File(expressionDir, "miku_default_wink.exp3.json"), defaultWinkExpressionJson());
            writeText(new File(expressionDir, "miku_default_surprise.exp3.json"), defaultSurpriseExpressionJson());

            JSONObject root = new JSONObject(readText(modelFile));
            JSONObject refs = root.optJSONObject("FileReferences");
            if (refs == null) {
                refs = new JSONObject();
                root.put("FileReferences", refs);
            }

            JSONArray expressions = refs.optJSONArray("Expressions");
            if (expressions == null) {
                expressions = new JSONArray();
                refs.put("Expressions", expressions);
            }

            expressions.put(new JSONObject()
                    .put("Name", "default_smile")
                    .put("File", "expressions_default/miku_default_smile.exp3.json"));
            expressions.put(new JSONObject()
                    .put("Name", "default_wink")
                    .put("File", "expressions_default/miku_default_wink.exp3.json"));
            expressions.put(new JSONObject()
                    .put("Name", "default_surprise")
                    .put("File", "expressions_default/miku_default_surprise.exp3.json"));

            writeText(modelFile, root.toString(2));
            return 3;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int countExpressionFiles(File root) {
        if (root == null || !root.exists()) {
            return 0;
        }

        if (root.isFile()) {
            String name = root.getName().toLowerCase();
            if (name.endsWith(".exp3.json") || name.endsWith(".expression.json")) {
                return 1;
            }
            return 0;
        }

        int count = 0;
        File[] list = root.listFiles();
        if (list != null) {
            for (File f : list) {
                count += countExpressionFiles(f);
            }
        }
        return count;
    }

    private static String expressionJson(String[] parameters) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("\"Type\":\"Live2D Expression\",\n");
        sb.append("\"FadeInTime\":0.4,\n");
        sb.append("\"FadeOutTime\":0.6,\n");
        sb.append("\"Parameters\":[\n");
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) sb.append(",\n");
            sb.append(parameters[i]);
        }
        sb.append("\n]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String expressionParam(String id, double value, String blend) {
        return "{\"Id\":\"" + id + "\",\"Value\":" + format(value) + ",\"Blend\":\"" + blend + "\"}";
    }

    private static String defaultSmileExpressionJson() {
        return expressionJson(new String[]{
                expressionParam("ParamMouthForm", 1.0, "Add"),
                expressionParam("ParamMouthOpenY", 0.18, "Add"),
                expressionParam("ParamEyeSmile", 1.0, "Add")
        });
    }

    private static String defaultWinkExpressionJson() {
        return expressionJson(new String[]{
                expressionParam("ParamEyeLOpen", -0.8, "Add"),
                expressionParam("ParamEyeROpen", 0.0, "Add"),
                expressionParam("ParamMouthForm", 0.7, "Add"),
                expressionParam("ParamMouthOpenY", 0.12, "Add")
        });
    }

    private static String defaultSurpriseExpressionJson() {
        return expressionJson(new String[]{
                expressionParam("ParamEyeLOpen", 0.3, "Add"),
                expressionParam("ParamEyeROpen", 0.3, "Add"),
                expressionParam("ParamMouthOpenY", 0.65, "Add"),
                expressionParam("ParamMouthForm", -0.2, "Add")
        });
    }

    private static int countMotionFiles(File root) {
        if (root == null || !root.exists()) {
            return 0;
        }

        if (root.isFile()) {
            String name = root.getName().toLowerCase();
            if (name.endsWith(".motion3.json")
                    || name.endsWith(".mtn")
                    || name.endsWith(".motion.json")) {
                return 1;
            }
            return 0;
        }

        int count = 0;
        File[] list = root.listFiles();
        if (list != null) {
            for (File f : list) {
                count += countMotionFiles(f);
            }
        }
        return count;
    }

    private static File findModelFile(File root) {
        if (root == null || !root.exists()) {
            return null;
        }

        File model3 = findBySuffix(root, ".model3.json");
        if (model3 != null) {
            return model3;
        }

        File modelJson = findByName(root, "model.json");
        if (modelJson != null) {
            return modelJson;
        }

        return findBySuffix(root, ".json");
    }

    private static File findByName(File dir, String name) {
        File[] list = dir.listFiles();
        if (list == null) {
            return null;
        }

        for (File f : list) {
            if (f.isFile() && name.equalsIgnoreCase(f.getName())) {
                return f;
            }
        }

        for (File f : list) {
            if (f.isDirectory()) {
                File hit = findByName(f, name);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static File findBySuffix(File dir, String suffix) {
        File[] list = dir.listFiles();
        if (list == null) {
            return null;
        }

        for (File f : list) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(suffix)) {
                return f;
            }
        }

        for (File f : list) {
            if (f.isDirectory()) {
                File hit = findBySuffix(f, suffix);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static DocumentMeta queryMeta(Context context, Uri docUri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    docUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                    },
                    null,
                    null,
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                return new DocumentMeta(cursor.getString(0), cursor.getString(1));
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return new DocumentMeta("Live2D模型", DocumentsContract.Document.MIME_TYPE_DIR);
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.length() == 0) {
            return "unnamed";
        }
        return name.replace("/", "_")
                .replace("\\", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_");
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            File[] list = f.listFiles();
            if (list != null) {
                for (File child : list) {
                    deleteRecursively(child);
                }
            }
        }
        try {
            f.delete();
        } catch (Throwable ignored) {
        }
    }

    private static class DocumentMeta {
        final String name;
        final String mimeType;

        DocumentMeta(String name, String mimeType) {
            this.name = name == null ? "" : name;
            this.mimeType = mimeType == null ? "" : mimeType;
        }
    }
}
