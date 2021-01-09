package com.lbynet.Phokus.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

/*
SAL stands for "Software Abstract Layer", a class that mainly addresses differences in operating systems.
 */
public class SAL {

    public enum MsgType {
        ERROR,
        DEBUG,
        VERBOSE,
        INFO,
        WARN,
    }

    public static ArrayList<String> log_ = new ArrayList<>();

    public static void print(String msg) {
        print(MsgType.VERBOSE,"DefaultTag",msg);
    }

    public static void print(MsgType type, String tag, String msg) {

        final String printMsg = new StringBuilder().append("[Phokus]").append(msg).toString(),
                     saveMsg  = String.format(getLogTemplate(),
                                                   new StringBuilder().append(Character.toLowerCase(type.toString().charAt(0)))
                                                   .append('/')
                                                   .append(tag).toString(),
                                                   msg);

        switch(type) {
            case ERROR:
                Log.e(tag,printMsg);
                break;
            case VERBOSE:
                Log.v(tag,printMsg);
                break;
            case DEBUG:
                Log.d(tag,printMsg);
                break;
            case INFO:
                Log.i(tag,printMsg);
                break;
            case WARN:
                Log.w(tag,printMsg);
                break;
        }

        log_.add(saveMsg);

    }

    public static String getLogTemplate() {
        return new StringBuilder().append(getTimestamp())
                .append(" %s: %s").toString();
    }

    public static String getTimestamp() {
        long timestamp = System.currentTimeMillis();
        return new SimpleDateFormat("yyyy-LL-dd HH:mm:ss").format(timestamp);
    }

    public static String getLogInString() {

        StringBuilder sb = new StringBuilder();

        for(String i : log_) {
            sb.append(i).append("\n\n");
        }

        return sb.toString();
    }

    public static void print(String tag, String msg) {
        print(MsgType.VERBOSE,tag,msg);
    }

    public static String getDeviceName(Context context) {

        String name = Settings.Secure.getString(context.getContentResolver(),"bluetooth_name");

        if(name == null) {
            name = Build.BRAND +" " + Build.MODEL + " " + Build.ID;
        }

        return name;
    }

    public static void print(Exception e) {


        StringBuilder err = new StringBuilder()
            .append(e.toString()).append("\n")
                .append("Message: ").append(e.getMessage()).append("\n")
                .append("Location: \n");

        for(StackTraceElement i : e.getStackTrace()) {
            err.append("\t" + i.getClassName() + "." + i.getMethodName() + "(Line " + i.getLineNumber() + ")\n");
        }

        Log.e("Exception", "[Fokus]Exception: " + err.toString());

        log_.add(String.format(getLogTemplate() ,"EXCEPTION",err.toString()));
    }

    public static void sleepFor(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception e) {
            //Shhhh
        }
    }

    public static void printUri(Uri uri, ContentResolver resolver) {

        Cursor query = resolver.query(uri,null,null,null,null,null);

        query.moveToFirst();

        print(MsgType.VERBOSE,
                "printUri",
                "Scheme: " + uri.getScheme() + "\n" +
                "Path: " + uri.getPath() + "\n" +
                "Authority: " + uri.getAuthority() + "\n" +
                "Query: " + uri.getQuery() + "\n" +
                "Type: " + resolver.getType(uri) + "\n");


        print(MsgType.VERBOSE,
                "printUri",
                "Filename (by query): " + query.getString(query.getColumnIndex(OpenableColumns.DISPLAY_NAME)));
    }
}
