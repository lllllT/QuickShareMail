package org.tamanegi.quicksharemail.mail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataSource;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

public class UriDataSource implements DataSource
{
    private ContentResolver resolver;
    private Uri uri;
    private String type;
    private String name;

    public UriDataSource(ContentResolver resolver, Uri uri)
    {
        this.resolver = resolver;
        this.uri = uri;
        this.type = null;
        this.name = null;
    }

    public String getContentType()
    {
        if(type == null) {
            type = resolver.getType(uri);
        }
        if(type == null) {
            type = "application/octet-stream";
        }

        return type;
    }

    public InputStream getInputStream() throws IOException
    {
        return resolver.openInputStream(uri);
    }

    public String getName()
    {
        if(name == null) {
            Cursor cur = resolver.query(
                uri,
                new String[] { OpenableColumns.DISPLAY_NAME },
                null, null, null);
            if(cur != null) {
                try {
                    if(cur.moveToFirst()) {
                        name = cur.getString(0);
                    }
                }
                finally {
                    cur.close();
                }
            }

            if(name == null) {
                name = uri.getLastPathSegment();
            }
        }

        return name;
    }

    public OutputStream getOutputStream() throws IOException
    {
        throw new IOException("UriDataSource: Output is not supported");
    }

}
