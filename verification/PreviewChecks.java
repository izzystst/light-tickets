package dev.steelthread.lighttickets.library.checks;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import dev.steelthread.lighttickets.Ticket;
import dev.steelthread.lighttickets.TicketRenderer;
import dev.steelthread.lighttickets.TicketStore;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Exercises the actual Android PDF decoder and private storage with generated files only. */
public final class PreviewChecks extends Instrumentation {
    private int checks;
    private void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    private byte[] pdf(int count)throws Exception{
        PdfDocument document=new PdfDocument();
        try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            for(int n=0;n<count;n++){
                PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(300,400,n).create());
                page.getCanvas().drawColor(android.graphics.Color.WHITE);
                Paint ink=new Paint();ink.setColor(android.graphics.Color.BLACK);ink.setTextSize(24);
                page.getCanvas().drawText("Practice page "+(n+1),20,60,ink);document.finishPage(page);
            }
            document.writeTo(bytes);return bytes.toByteArray();
        }finally{document.close();}
    }
    private byte[] png()throws Exception{
        Bitmap image=Bitmap.createBitmap(40,60,Bitmap.Config.ARGB_8888);image.eraseColor(android.graphics.Color.WHITE);
        try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            image.compress(Bitmap.CompressFormat.PNG,100,bytes);return bytes.toByteArray();
        }finally{image.recycle();}
    }
    private void rejects(TicketStore store,byte[] bytes,String name)throws Exception{
        int before=store.list().size();boolean refused=false;
        try{store.importTicket(new ByteArrayInputStream(bytes),name,false);}catch(Exception expected){refused=true;}
        check(refused && store.list().size()==before,"rejected input leaves shelf intact: "+name);
    }
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();File root=null;
        try{
            root=Files.createTempDirectory(getTargetContext().getCacheDir().toPath(),"sdk-ticket-checks-").toFile();
            TicketStore store=new TicketStore(root);
            check(store.list().isEmpty(),"new shelf empty");
            byte[] original=pdf(2);
            Ticket ticket=store.importTicket(new ByteArrayInputStream(original),"Practice\u202eticket.pdf",false);
            check(ticket.getKind().equals("pdf") && ticket.getPages()==2,"PDF page count detected");
            check(!ticket.getTitle().contains("\u202e"),"misleading filename control removed");
            check(Arrays.equals(original,Files.readAllBytes(store.file(ticket).toPath())),"original PDF bytes preserved");
            check(new TicketStore(root).list().size()==1,"shelf survives reopening");
            Bitmap rendered=TicketRenderer.INSTANCE.render(store.file(ticket),"pdf",1);
            check(rendered.getWidth()>0 && rendered.getHeight()>0 && (long)rendered.getWidth()*rendered.getHeight()<=8_000_000,"second PDF page rendered within memory bound");rendered.recycle();
            byte[] image=png();Ticket imageTicket=store.importTicket(new ByteArrayInputStream(image),"image.png",false);
            check(imageTicket.getKind().equals("image") && imageTicket.getPages()==1,"PNG recognized");
            rendered=TicketRenderer.INSTANCE.render(store.file(imageTicket),"image",0);
            check(rendered.getWidth()==40 && rendered.getHeight()==60,"PNG rendered correctly");rendered.recycle();
            rejects(store,new byte[0],"empty.pdf");
            rejects(store,"%PDF-broken".getBytes(),"broken.pdf");
            rejects(store,"not a ticket".getBytes(),"wrong.png");
            rejects(store,pdf(101),"too-many-pages.pdf");
            check(store.remove(ticket) && !store.file(ticket).exists(),"remove deletes saved copy");
            check(store.list().size()==1,"remove preserves other ticket");
            check(store.remove(imageTicket) && store.list().isEmpty(),"shelf empty after cleanup");
            File[] partial=new File(root,"tickets").listFiles((dir,name)->name.endsWith(".partial"));
            check(partial!=null && partial.length==0,"no interrupted imports remain");
            result.putString("stream","PASS: "+checks+" native SDK ticket checks\n");result.putInt("checks",checks);
            finish(-1,result);
        }catch(Throwable error){result.putString("stream","FAIL after "+checks+": "+error+"\n");finish(0,result);}
        finally{if(root!=null)removeTree(root);}
    }
    private void removeTree(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)removeTree(child);file.delete();}
}
