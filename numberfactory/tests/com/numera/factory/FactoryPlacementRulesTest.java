package com.numera.factory;

/** Host-JDK regression tests: all miner outlets and per-machine rotations. */
public final class FactoryPlacementRulesTest {
    private static void check(boolean pass,String message) {
        if(!pass)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int[][] sockets={
            {2,-1,0},{2,0,0},{2,1,0},
            {1,2,1},{0,2,1},{-1,2,1},
            {-2,1,2},{-2,0,2},{-2,-1,2},
            {-1,-2,3},{0,-2,3},{1,-2,3}
        };
        int checks=0;
        for(int[] s:sockets) {
            int incoming=ConveyorJunctions.incomingFromMine(s[0],s[1]);
            check(incoming==s[2],"incorrect miner socket "+s[0]+","+s[1]);
            for(int direction=0;direction<4;direction++) {
                boolean elbow=incoming!=direction&&incoming!=(direction+2)%4;
                check(elbow==(direction==(incoming+1)%4
                           ||direction==(incoming+3)%4),"socket elbow classification");
                checks++;
            }
            checks++;
        }
        for(int dx=-3;dx<=3;dx++)
            for(int dy=-3;dy<=3;dy++) {
                boolean allowed=false;
                for(int[] s:sockets)if(s[0]==dx&&s[1]==dy)allowed=true;
                check((ConveyorJunctions.incomingFromMine(dx,dy)>=0)==allowed,
                      "false mine socket "+dx+","+dy);
                checks++;
            }
        int[] kinds={3,4,7,8,9,10};
        MachineOrientationMemory m=new MachineOrientationMemory(kinds);
        for(int type:kinds){check(m.rotationFor(type)==0,"nonzero default");checks++;}
        check(m.remember(3,1),"cannot rotate addition");
        check(m.rotationFor(3)==1,"new addition did not inherit rotation");
        check(m.rotationFor(4)==0,"rotation leaked between machine types");
        check(m.remember(4,2),"cannot rotate multiplication");
        check(m.rotationFor(3)==1&&m.rotationFor(4)==2,"per-kind rotations lost");
        check(m.remember(3,5)&&m.rotationFor(3)==1,"rotation not normalized");
        check(!m.remember(1,3)&&m.rotationFor(1)==0,"belt mistakenly saved");
        checks+=6;
        System.out.println("Factory placement rules: OK, "+checks+" assertions");
    }
}
