package com.numera.factory;

import java.util.ArrayList;

/**
 * One authoritative, rotatable occupancy and port map for each operator.
 * The table is independent of Canvas, gestures, movement and persistence.
 *
 * Inspired by the tile footprints of Beltmatic (no imported art/assets).
 * 'A' and 'B': operand intakes; 'O': primary result; 'R': remainder;
 * 'S': bidirectional storage; '.': unoccupied (L-shaped) tile.
 *
 * Top-to-bottom default orientations:
 *   Addition      OO / AB     (2x2)
 *   Multiplication AOB         (3x1)
 *   Subtraction   BO / .A      (L, 3 occupied)
 *   Division      OR / AB     (2x2)
 *   Exponent      BO / .A      (L, 3 occupied)
 *   Storage       SSS/SSS/SSS  (3x3)
 */
final class MachineLayout {
    static final int A=1,B=2,OUTPUT=3,REMAINDER=4,STORAGE=5;
    static final class Cell {
        final int x,y,port;
        Cell(int x,int y,int port){this.x=x;this.y=y;this.port=port;}
    }
    static final class Shape {
        final int width,height;
        final Cell[] cells;
        Shape(int width,int height,Cell[] cells) {
            this.width=width;this.height=height;this.cells=cells;
        }
        int portAt(int x,int y) {
            for(Cell c:cells)if(c.x==x&&c.y==y)return c.port;
            return 0;
        }
    }
    private static String[] blueprint(int type) {
        switch(type) {
            case 3:return new String[]{"OO","AB"};
            case 4:return new String[]{"AOB"};
            case 7:return new String[]{"BO",".A"};
            case 8:return new String[]{"OR","AB"};
            case 9:return new String[]{"BO",".A"};
            case 10:return new String[]{"SSS","SSS","SSS"};
            default:return null;
        }
    }
    static boolean isMachine(int type){return blueprint(type)!=null;}
    static Shape shape(int type,int rotation) {
        String[] rows=blueprint(type);
        if(rows==null)return null;
        int h=rows.length,w=rows[0].length(),r=rotation&3;
        int rotatedW=(r&1)==0?w:h, rotatedH=(r&1)==0?h:w;
        ArrayList<Cell> tiles=new ArrayList<>();
        for(int y=0;y<h;y++)
            for(int x=0;x<w;x++) {
                char label=rows[y].charAt(x);
                int port=label=='A'?A:label=='B'?B:
                         label=='O'?OUTPUT:label=='R'?REMAINDER:
                         label=='S'?STORAGE:0;
                if(port==0)continue;
                int tx=r==0?x:r==1?h-1-y:r==2?w-1-x:y;
                int ty=r==0?y:r==1?x:r==2?h-1-y:w-1-x;
                tiles.add(new Cell(tx,ty,port));
            }
        return new Shape(rotatedW,rotatedH,tiles.toArray(new Cell[0]));
    }
    private MachineLayout(){}
}
