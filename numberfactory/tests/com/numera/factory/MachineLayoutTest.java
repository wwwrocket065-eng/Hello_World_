package com.numera.factory;

import java.util.HashSet;

/** Host-side regression tests; no Android SDK or external test framework needed. */
public final class MachineLayoutTest {
    private static void require(boolean b,String msg) {
        if(!b)throw new AssertionError(msg);
    }
    private static final int[][] EXPECTED={
        {3,2,2,4,1,1,2,0},{4,3,1,3,1,1,1,0},
        {7,2,2,3,1,1,1,0},{8,2,2,4,1,1,1,1},
        {9,2,2,3,1,1,1,0},{10,3,3,9,0,0,0,0}
    };
    public static void main(String[] args) {
        int checks=0;
        for(int[] spec:EXPECTED) {
            int type=spec[0];
            for(int rot=0;rot<4;rot++) {
                MachineLayout.Shape sh=MachineLayout.shape(type,rot);
                require(sh!=null,"missing shape for "+type);
                int w=(rot&1)==0?spec[1]:spec[2];
                int h=(rot&1)==0?spec[2]:spec[1];
                require(sh.width==w&&sh.height==h,"wrong size "+type+"/"+rot);
                require(sh.cells.length==spec[3],"wrong footprint "+type+"/"+rot);
                int[] counts=new int[6];
                HashSet<Integer> occupied=new HashSet<>();
                for(MachineLayout.Cell c:sh.cells) {
                    require(c.x>=0&&c.y>=0&&c.x<w&&c.y<h,"out of bounds");
                    require(occupied.add(c.y*w+c.x),"duplicate tile "+type);
                    require(sh.portAt(c.x,c.y)==c.port,"port mismatch");
                    counts[c.port]++;
                    if(c.port!=MachineLayout.STORAGE) {
                        boolean exposed=false;
                        for(int d=0;d<4;d++) {
                            int nx=c.x+(d==0?1:d==2?-1:0);
                            int ny=c.y+(d==1?1:d==3?-1:0);
                            if(sh.portAt(nx,ny)==0)exposed=true;
                        }
                        require(exposed,"no external connection for tile "+type);
                    }
                    checks+=3;
                }
                require(counts[MachineLayout.A]==spec[4],"A ports "+type);
                require(counts[MachineLayout.B]==spec[5],"B ports "+type);
                require(counts[MachineLayout.OUTPUT]==spec[6],"result ports "+type);
                require(counts[MachineLayout.REMAINDER]==spec[7],"remainder ports "+type);
                require(counts[MachineLayout.STORAGE]==(type==10?9:0),"storage ports");
                checks+=10;
            }
        }
        require(MachineLayout.shape(1,0)==null,"belt cannot be a machine");
        System.out.println("MachineLayout: OK, "+checks+" assertions across all 6 types x 4 rotations");
    }
}
