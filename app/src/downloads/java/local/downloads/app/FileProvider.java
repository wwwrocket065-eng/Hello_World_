package local.downloads.app;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.ParcelFileDescriptor;import android.provider.OpenableColumns;import java.io.*;
public final class FileProvider extends ContentProvider{
 public boolean onCreate(){return true;}
 Store.Task task(Uri u){if(u.getPathSegments().size()!=2)return null;Store.Task t=Store.get(getContext()).get(u.getPathSegments().get(0));return t!=null&&Store.DONE.equals(t.status)?t:null;}
 public String getType(Uri u){Store.Task t=task(u);return t==null?"application/octet-stream":t.mime;}
 public Cursor query(Uri u,String[] p,String s,String[] args,String sort){Store.Task t=task(u);String[] cols=p==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:p;MatrixCursor c=new MatrixCursor(cols);if(t!=null){Object[] values=new Object[cols.length];for(int i=0;i<cols.length;i++)if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))values[i]=t.name;else if(OpenableColumns.SIZE.equals(cols[i]))values[i]=t.got;c.addRow(values);}return c;}
 public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{Store.Task t=task(u);if(t==null||!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(Store.get(getContext()).data(t),ParcelFileDescriptor.MODE_READ_ONLY);}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}