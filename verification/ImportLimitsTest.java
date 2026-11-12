package dev.steelthread.lighttickets;
import java.io.*;
import java.util.Arrays;
public final class ImportLimitsTest {
    private static int checks;
    private static void check(boolean b){if(!b)throw new AssertionError("check "+checks);checks++;}
    public static void main(String[] args)throws Exception{
        for(int size:new int[]{1,16383,16384,16385,(int)ImportLimits.MAX_BYTES}){
            byte[] bytes=new byte[size];Arrays.fill(bytes,(byte)37);ByteArrayOutputStream out=new ByteArrayOutputStream();
            check(ImportLimits.INSTANCE.copy(new ByteArrayInputStream(bytes),out)==size);check(Arrays.equals(bytes,out.toByteArray()));
        }
        for(int size:new int[]{0,(int)ImportLimits.MAX_BYTES+1}){
            boolean fail=false;try{ImportLimits.INSTANCE.copy(new ByteArrayInputStream(new byte[size]),new ByteArrayOutputStream());}catch(Exception e){if(!(e instanceof IOException))throw e;fail=true;}check(fail);
        }
        check(ImportLimits.INSTANCE.cleanName(null).equals("Imported ticket"));check(ImportLimits.INSTANCE.cleanName("\n\u202E").equals("Imported ticket"));
        check(ImportLimits.INSTANCE.cleanName(" x\n y ").equals("x  y"));check(ImportLimits.INSTANCE.cleanName("x".repeat(300)).length()==120);
        System.out.println("PASS: "+checks+" import boundary checks");
    }
}
