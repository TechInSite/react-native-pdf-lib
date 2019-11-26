package com.hopding.pdflib.factories;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.support.annotation.RequiresPermission;
import android.content.Context;
import android.content.res.AssetManager;

import com.facebook.imagepipeline.core.ImagePipelineFactory;
import com.facebook.react.bridge.NoSuchKeyException;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * Create a PDPage object and applies actions described in JSON
 * to it, such as drawing text or images. The PDPage object can
 * be created anew, or from an existing document.
 */
public class PDPageFactory {
    protected PDDocument document;
    protected PDPage page;
    protected PDPageContentStream stream;
    private static AssetManager ASSET_MANAGER = null;

    public static void init(Context context){
        if (ASSET_MANAGER == null) {
            ASSET_MANAGER = context.getApplicationContext().getAssets();
        }
    }

    private PDPageFactory(PDDocument document, PDPage page, boolean appendContent) throws IOException {
        this.document = document;
        this.page     = page;
        this.stream   = new PDPageContentStream(document, page, appendContent, true, true);
    }

            /* ----- Factory methods ----- */
    protected static PDPage create(PDDocument document, ReadableMap pageActions) throws IOException {
        PDPage page = new PDPage();
        PDPageFactory factory = new PDPageFactory(document, page, false);

        factory.setMediaBox(pageActions.getMap("mediaBox"));
        factory.applyActions(pageActions);
        factory.stream.close();
        return page;
    }

    protected static PDPage modify(PDDocument document, ReadableMap pageActions) throws IOException {
        int pageIndex = pageActions.getInt("pageIndex");
        PDPage page   = document.getPage(pageIndex);
        PDPageFactory factory = new PDPageFactory(document, page, true);

        factory.applyActions(pageActions);
        factory.stream.close();
        return page;
    }

            /* ----- Page actions (based on JSON structures sent over bridge) ----- */
    private void applyActions(ReadableMap pageActions) throws IOException {
        ReadableArray actions = pageActions.getArray("actions");
        for(int i = 0; i < actions.size(); i++) {
            ReadableMap action = actions.getMap(i);
            String type = action.getString("type");

            if (type.equals("text"))
                this.drawText(action);
            else if (type.equals("rectangle"))
                this.drawRectangle(action);
            else if (type.equals("image"))
                this.drawImage(action);
        }
    }

    private void setMediaBox(ReadableMap dimensions) {
        Float[] coords = getCoords(dimensions, true);
        Float[] dims   = getDims(dimensions, true);
        page.setMediaBox(new PDRectangle(coords[0].floatValue(), coords[1].floatValue(), dims[0].floatValue(), dims[1].floatValue()));
    }

    private void drawText(ReadableMap textActions) throws NoSuchKeyException, IOException {
        String value = textActions.getString("value");
        String fontName = textActions.getString("fontName");
        int fontSize = textActions.getInt("fontSize");

        Float[] coords = getCoords(textActions, true);
        int[] rgbColor   = hexStringToRGB(textActions.getString("color"));

        PDFont font = PDType0Font.load(document, ASSET_MANAGER.open("fonts/" + fontName + ".ttf"));

        stream.beginText();
        stream.setNonStrokingColor(rgbColor[0], rgbColor[1], rgbColor[2]);
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(coords[0].floatValue(), coords[1].floatValue());
        stream.showText(value);
        stream.endText();
    }

    private void drawRectangle(ReadableMap rectActions) throws NoSuchKeyException, IOException {
        Float[] coords = getCoords(rectActions, true);
        Float[] dims   = getDims(rectActions, true);
        int[] rgbColor   = hexStringToRGB(rectActions.getString("color"));

        stream.addRect(coords[0].floatValue(), coords[1].floatValue(), dims[0].floatValue(), dims[1].floatValue());
        stream.setNonStrokingColor(rgbColor[0], rgbColor[1], rgbColor[2]);
        stream.fill();
    }

    private static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private static Bitmap applyRotationTransform(String imagePath, Bitmap imageBitmap) throws IOException {
        ExifInterface ei = new ExifInterface(imagePath);
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

        Bitmap rotatedBitmap;
        switch(orientation) {

            case ExifInterface.ORIENTATION_ROTATE_90:
                rotatedBitmap = rotateImage(imageBitmap, 90);
                break;

            case ExifInterface.ORIENTATION_ROTATE_180:
                rotatedBitmap = rotateImage(imageBitmap, 180);
                break;

            case ExifInterface.ORIENTATION_ROTATE_270:
                rotatedBitmap = rotateImage(imageBitmap, 270);
                break;

            case ExifInterface.ORIENTATION_NORMAL:
            default:
                rotatedBitmap = imageBitmap;
        }
        return rotatedBitmap;
    }

    private void drawImage(ReadableMap imageActions) throws NoSuchKeyException, IOException {
        String imagePath = imageActions.getString("imagePath");
        String imageSource = imageActions.getString("source");

        Float[] coords = getCoords(imageActions, true);
        Float[] dims   = getDims(imageActions, false);

        PDImageXObject pdfImage = null;
        URI imagePathURI = URI.create(imagePath);

        if (imageSource.equals("path")) {
            File fileToOpen = new File(imagePathURI);
            InputStream in = new FileInputStream(fileToOpen);
            Bitmap bmp = BitmapFactory.decodeStream(in);
            Bitmap rotatedImage = applyRotationTransform(imagePathURI.getPath(), bmp);
            pdfImage  = LosslessFactory.createFromImage(document, rotatedImage);
            in.close();
        }

        if (imageSource.equals("assets")) {
            InputStream is = ASSET_MANAGER.open(imagePath);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            Bitmap rotatedImage = applyRotationTransform(imagePath, bmp);
            pdfImage  = LosslessFactory.createFromImage(document, rotatedImage);
        }

        // Draw the PDImageXObject to the stream
        if (dims[0] != null && dims[1] != null) {
            stream.drawImage(pdfImage , coords[0].floatValue(), coords[1].floatValue(), dims[0].floatValue(), dims[1].floatValue());
        }
        else {
            stream.drawImage(pdfImage , coords[0].floatValue(), coords[1].floatValue());
        }
    }

            /* ----- Static utilities ----- */
    private static Float[] getDims(ReadableMap dimsMap, boolean required) {
        return getFloatKeyPair(dimsMap, "width", "height", required);
    }

    private static Float[] getCoords(ReadableMap coordsMap, boolean required) {
        return getFloatKeyPair(coordsMap, "x", "y", required);
    }

    private static Float[] getFloatKeyPair(ReadableMap map, String key1, String key2, boolean required) {
        Double val1 = null;
        Double val2 = null;
        try {
            val1 = map.getDouble(key1);
            val2 = map.getDouble(key2);
        } catch (NoSuchKeyException e) {
            if (required) throw e;
        }
        return new Float[] { val1.floatValue(), val2.floatValue() };
    }

    // We get a color as a hex string, e.g. "#F0F0F0" - so parse into RGB vals
    private static int[] hexStringToRGB(String hexString) {
        int colorR = Integer.valueOf( hexString.substring( 1, 3 ), 16 );
        int colorG = Integer.valueOf( hexString.substring( 3, 5 ), 16 );
        int colorB = Integer.valueOf( hexString.substring( 5, 7 ), 16 );
        return new int[] { colorR, colorG, colorB };
    }
}
