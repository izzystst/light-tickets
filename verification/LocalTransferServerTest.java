package dev.steelthread.lighttickets;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Actual socket requests test access boundaries and lifecycle, not parser implementation details. */
public final class LocalTransferServerTest {
    private static int checks;
    private static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);checks++;}
    private static final Map<String,byte[]> ASSETS=Map.of("/","<html>receiver</html>".getBytes(),"/app.js","demo".getBytes());
    private static String request(LocalTransferServer s,String method,String path,Map<String,String> headers,byte[] body)throws Exception{
        URI address=URI.create(s.getAddress());
        try(Socket socket=new Socket(address.getHost(),address.getPort())){
            socket.setSoTimeout(3000);
            StringBuilder head=new StringBuilder(method+" "+path+" HTTP/1.1\r\nHost: "+address.getAuthority()+"\r\n");
            headers.forEach((k,v)->head.append(k).append(": ").append(v).append("\r\n"));
            if(body!=null)head.append("Content-Length: ").append(body.length).append("\r\n");
            socket.getOutputStream().write((head+"\r\n").getBytes(StandardCharsets.US_ASCII));
            if(body!=null)socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
            InputStream input=socket.getInputStream();ByteArrayOutputStream responseHeader=new ByteArrayOutputStream();
            String headText="";
            while(!headText.endsWith("\r\n\r\n")){
                int n=input.read();if(n<0)throw new EOFException("no HTTP response");responseHeader.write(n);
                headText=responseHeader.toString(StandardCharsets.US_ASCII);
            }
            int length=0;
            for(String line:headText.split("\r\n"))if(line.startsWith("Content-Length: "))length=Integer.parseInt(line.substring(16));
            byte[] responseBody=input.readNBytes(length);if(responseBody.length!=length)throw new EOFException("short HTTP response");
            return headText+new String(responseBody,StandardCharsets.UTF_8);
        }
    }
    private static String key(LocalTransferServer s){return s.getPairingUrl().split("#key=")[1];}
    private static Map<String,String> auth(LocalTransferServer s){return Map.of("Authorization","Bearer "+key(s));}
    private static boolean status(String result,int status){return result.startsWith("HTTP/1.1 "+status+" ");}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("light-transfer-tests-");
        BlockingQueue<LocalTransferServer.Pending> inbox=new LinkedBlockingQueue<>();
        try(LocalTransferServer s=new LocalTransferServer(InetAddress.getLoopbackAddress(),dir.toFile(),ASSETS,"test-script-hash",p->{inbox.add(p);return kotlin.Unit.INSTANCE;},30_000,0)){
            check(status(request(s,"GET","/",Map.of(),null),200),"uploader page available");
            check(status(request(s,"GET","/status",Map.of(),null),403),"status requires pairing");
            check(status(request(s,"GET","/status",auth(s),null),200),"paired status works");
            check(status(request(s,"GET","/status",Map.of("Authorization","Bearer "+key(s),"Origin","http://evil.invalid"),null),403),"cross-origin denied");
            check(status(request(s,"GET","/status",Map.of("Authorization","Bearer "+key(s),"Sec-Fetch-Site","cross-site"),null),403),"cross-site denied");
            check(status(request(s,"GET","/tickets/anything",auth(s),null),404),"no saved ticket route");
            check(status(request(s,"GET","/../ticket_index.xml",auth(s),null),404),"no traversal route");
            check(status(request(s,"POST","/pair",Map.of(),s.getPairingCode().getBytes()),200),"laptop pairing works");
            check(status(request(s,"POST","/upload",Map.of("X-Ticket-Name","ticket.pdf"),new byte[]{1,2}),403),"upload requires pairing");
            byte[] pdf="%PDF-demonstration bytes".getBytes();
            check(status(request(s,"POST","/upload",Map.of("Authorization","Bearer "+key(s),"X-Ticket-Name","event%20ticket.pdf"),pdf),202),"upload waits for phone");
            LocalTransferServer.Pending p=inbox.poll(1,TimeUnit.SECONDS);
            check(p!=null && p.getName().equals("event ticket.pdf") && Arrays.equals(Files.readAllBytes(p.getFile().toPath()),pdf),"bytes and filename intact");
            check(s.getState().equals("pending"),"not auto-saved");
            check(status(request(s,"POST","/upload",Map.of("Authorization","Bearer "+key(s),"X-Ticket-Name","again.pdf"),pdf),409),"one file per session");
            s.saving();s.finish(true);
            check(!p.getFile().exists(),"staging file removed after save");
            check(request(s,"GET","/status",auth(s),null).contains("\"saved\""),"browser sees save confirmation");
        }
        try(LocalTransferServer s=new LocalTransferServer(InetAddress.getLoopbackAddress(),dir.toFile(),ASSETS,"test-script-hash",p->{inbox.add(p);return kotlin.Unit.INSTANCE;},30_000,0)){
            check(status(request(s,"POST","/upload",Map.of("Authorization","Bearer "+key(s),"X-Ticket-Name","x.pdf","Transfer-Encoding","chunked"),null),400),"ambiguous length rejected");
            check(status(request(s,"POST","/upload",Map.of("Authorization","Bearer "+key(s),"X-Ticket-Name","x.pdf","Content-Length","20971521"),null),400),"oversize rejected before body");
            check(status(request(s,"GET","/",Map.of("Host","other.invalid"),null),400),"duplicate host rejected");
            check(status(request(s,"POST","/upload",Map.of("Authorization","Bearer "+key(s),"X-Ticket-Name","%broken"),new byte[]{1}),400),"invalid filename encoding rejected");
            check(status(request(s,"POST","/upload",Map.of("Authorization","Bearer "+key(s),"X-Ticket-Name","x.pdf"),new byte[0]),400),"empty upload rejected");
        }
        LocalTransferServer s=new LocalTransferServer(InetAddress.getLoopbackAddress(),dir.toFile(),ASSETS,"test-script-hash",p->{inbox.add(p);return kotlin.Unit.INSTANCE;},30_000,0);
        String oldKey=key(s);String wrong=s.getPairingCode().equals("000000")?"000001":"000000";
        for(int n=0;n<5;n++)check(status(request(s,"POST","/pair",Map.of(),wrong.getBytes()),403),"wrong code refused");
        check(s.getState().equals("closed"),"five wrong codes stop receiver");
        try(LocalTransferServer fresh=new LocalTransferServer(InetAddress.getLoopbackAddress(),dir.toFile(),ASSETS,"test-script-hash",p->{inbox.add(p);return kotlin.Unit.INSTANCE;},30_000,0)){
            check(!oldKey.equals(key(fresh)),"new session has fresh key");
            check(status(request(fresh,"GET","/status",Map.of("Authorization","Bearer "+oldKey),null),403),"old key invalid");
        }
        LocalTransferServer fixed=new LocalTransferServer(InetAddress.getLoopbackAddress(),dir.toFile(),ASSETS,"test-script-hash",p->{inbox.add(p);return kotlin.Unit.INSTANCE;},30_000,0);
        URI fixedAddress=URI.create(fixed.getAddress());String previousKey=key(fixed);
        check(status(request(fixed,"GET","/",Map.of(),null),200),"bookmark session serves a real request before restart");fixed.close();
        try(LocalTransferServer restarted=new LocalTransferServer(InetAddress.getLoopbackAddress(),dir.toFile(),ASSETS,"test-script-hash",p->{inbox.add(p);return kotlin.Unit.INSTANCE;},30_000,fixedAddress.getPort())){
            check(restarted.getAddress().equals(fixedAddress.toString()),"bookmark address survives restart");
            check(!previousKey.equals(key(restarted)),"same address retains fresh session key");
            check(status(request(restarted,"GET","/status",Map.of("Authorization","Bearer "+previousKey),null),403),"saved old key remains invalid on fixed address");
        }
        LocalTransferServer expires=new LocalTransferServer(InetAddress.getLoopbackAddress(),dir.toFile(),ASSETS,"test-script-hash",p->{inbox.add(p);return kotlin.Unit.INSTANCE;},100,0);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(!expires.getState().equals("closed")&&System.nanoTime()<deadline)Thread.sleep(10);
        check(expires.getState().equals("closed"),"automatic expiry closes receiver");
        try(var files=Files.list(dir)){check(files.findAny().isEmpty(),"no pending data left");}
        Files.delete(dir);System.out.println("PASS: "+checks+" wireless transfer checks");
    }
}
