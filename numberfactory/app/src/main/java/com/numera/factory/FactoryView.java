package com.numera.factory;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Collections;

/**
 * Self-contained animated 2D factory prototype. All art is drawn in Canvas:
 * no copied game textures, no network and no runtime permissions.
 */
public final class FactoryView extends View {
    private static final int EMPTY = Integer.MIN_VALUE;
    private static final int BELT=1, SOURCE=2, ADD=3, MULTIPLY=4, HUB=5, BRIDGE=6;
    private static final int SUBTRACT=7, DIVIDE=8, EXPONENT=9, STORAGE=10, PART=11;
    private static final int[] OPERATORS={ADD,MULTIPLY,SUBTRACT,DIVIDE,EXPONENT,STORAGE};
    private static final String[] OP_SYMBOLS={"+","×","−","÷","^","▣"};
    private static final int[] OP_LEVELS={1,7,13,16,19,22};
    private static final int[] DX={1,0,-1,0}, DY={0,1,0,-1};
    private static final int BG=0xff071420, CYAN=0xff37c8c2, GOLD=0xffffc77c;
    private static final int PURPLE=0xffb08aff, BLUE=0xff6ca9ff;
    private static final String[] TOOLS={"Лента","Добыча","Сумма","Умнож.","Удалить","Выдел.","Вставить","Камера"};
    private static final String[] ICONS={"➜","◈","+","×","⌫","▧","▣","✥"};
    private static final int HUB_X=10, HUB_Y=9, HUB_SIZE=6;
    // Starter deposits are irregularly spaced outside the hub safety radius.
    // Number fields occupy 3x3 visually; the mineral source is the centre tile.
    private static final int[][] START_DEPOSITS={
        // Many number-1 options for the first level, but each field has
        // a wide empty margin for conveyor routing.
        {36,5,1},{3,38,1},{-19,0,1},{15,-25,1},{48,42,1},
        {-38,46,1},{65,-23,1},{-34,-40,1},{6,82,1},
        {78,52,2},{-58,2,2},{39,-56,2},{-29,82,2},
        {80,12,3},{-8,-70,3},{84,-60,4},{-73,61,4},{43,90,8}
    };
    private static final int DEPOSIT_REGION=46;
    private static final int MIN_DEPOSIT_SEPARATION=30;
    private static final float TOOLBAR_DP=94f, BELT_MENU_DP=44f;
    private static final int[][] MINE_OUTPUTS={{2,-1},{2,0},{2,1},{1,2},{0,2},{-1,2},{-2,1},{-2,0},{-2,-1},{-1,-2},{0,-2},{1,-2}};
    private final Map<Long,Node> cells = new HashMap<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final SharedPreferences prefs;
    private final float density;
    private float panX,panY,tile,zoom=.38f;
    private float clock,stepAccumulator,sourceAccumulator,flash;
    private long lastFrame;
    private int selected=7, level=1,goal=1,required=10,delivered,allDelivered;
    private int beltMode=0, operatorChoice=ADD, machineRotation=0,
                selectX0,selectY0,selectX1,selectY1,clipboardCount;
    private boolean initialized,pinching,dragged,toolbarGesture,beltMenuGesture,routeGesture,paintGesture;
    private boolean paintStartedOnBelt;
    private boolean eraseGesture,selectionGesture,selectionReady,selectionMenuGesture,routeMenuGesture,machineMenuGesture;
    private float lastX,lastY,lastSpan,downX,downY,gestureX,gestureY;
    private int priorGridX,priorGridY;
    private final ArrayList<Point> route=new ArrayList<>();
    private Point routeLive;
    private final Map<Long,Node> clipboard=new HashMap<>();
    private static final String[] UPGRADE_NAMES={"Ленты","Добытчики","Сумматоры","Умножители","Вычит./Делен.","Степени"};
    // Amounts for the first eight tiers follow published Beltmatic upgrade tables.
    // The original varies some requested number IDs between worlds.
    private static final int[][][][] UPGRADE_REQ={{{{1,200}},{{1,1000},{16,500}},{{1,4000},{16,3000},{22,1500}},{{34,9000},{121,7000},{839,6000}},{{94,9000},{247,7000},{562,6000}},{{291,11000},{791,9000},{2319,8000}},{{854,15000},{1526,12000},{7891,10000}},{{1526,20000},{7891,16000},{4294,12000}}},{{{2,300}},{{2,2000},{18,1000}},{{2,5000},{18,2000},{23,1000}},{{16,8000},{22,3000},{438,2000}},{{89,10000},{378,4000},{787,3000}},{{291,12000},{791,8000},{2319,4000}},{{791,15000},{2319,12000},{7774,6000}},{{2319,20000},{7774,17000},{929,20000}}},{{{3,450}},{{3,3000},{11,1500}},{{3,5000},{11,4000},{75,3000}},{{11,7000},{91,5000},{193,4000}},{{91,12000},{193,10000},{625,5000}},{{193,15000},{625,12000},{3447,8000}},{{625,20000},{3447,17000},{9423,10000}},{{3447,30000},{9423,20000},{44031,16000}}},{{{15,500}},{{15,5000},{20,2500}},{{15,10000},{20,7000},{257,4000}},{{20,11000},{257,6000},{690,5000}},{{257,17000},{690,13000},{3644,6000}},{{690,22000},{3644,17000},{6433,10000}},{{3644,35000},{6433,25000},{49514,17000}},{{6433,37000},{49514,30000},{97001,20000}}}};
    private static final int[] GOAL_SEQUENCE={1,2,3,5,10,19,27,36,47,50,82,174,248,324,466,594,631,727,891,928,1779,2805,3366,4860,5445,6667,7512,8133,9646,10904};
    private static final int[] GOAL_COUNTS={10,20,35,40,50,100,150,300,450,500,600,800,1000,2000,3000,4000,5000,6000,7000,8000,9000,10000,10500,11000,11500,12000,12500,13000,13500,14000};
    // Extra progression tracks are isolated from the legacy four save columns.
    // Early costs are provisional until verified against a game-world seed.
    private static final int[][][][] ADV_UPGRADE_REQ={
        {{{13,800}},{{13,6000},{75,3000}},{{13,10000},{75,7000},{147,4000}},
         {{147,13000},{640,8000},{3103,6000}},
         {{640,24000},{3103,16000},{8118,8000}},
         {{3103,32000},{8118,21000},{30870,13000}},
         {{8118,43000},{30870,33000},{61589,26000}},
         {{30870,50000},{61589,40000},{227887,30000}}},
        {{{64,800}},{{64,7000},{187,4000}},{{64,10000},{187,6000},{983,4000}},
         {{187,15000},{983,9000},{4563,7000}},
         {{983,29000},{4563,18000},{5684,9000}},
         {{4563,37000},{5684,25000},{6605,15000}},
         {{5684,40000},{11067,35000},{92927,26000}},
         {{11067,60000},{92927,50000},{8312675,40000}}}
    };
    private final int[] upgradeLevels={1,1,1,1,1,1};
    private final ArrayList<Map<Integer,Long>> upgradeBanks=new ArrayList<>();
    private boolean upgradePanel,upgradeGesture,headerGesture;
    private Node focusedMachine,pressedMachine;
    private boolean editMoveMode,rotateIconGesture,editDragGesture;
    private boolean machinePressActive,longPressTriggered;
    private int editTargetX,editTargetY,editFingerDX,editFingerDY;
    private String machineEditMessage="";
    private float machineEditMessageUntil;
    private final Runnable machineLongPress=new Runnable() {
        @Override public void run() {
            if(!machinePressActive||pressedMachine==null||pinching||dragged)return;
            focusedMachine=pressedMachine;
            editMoveMode=true;
            editDragGesture=true;
            editFingerDX=priorGridX-focusedMachine.originX;
            editFingerDY=priorGridY-focusedMachine.originY;
            editTargetX=focusedMachine.originX;
            editTargetY=focusedMachine.originY;
            longPressTriggered=true;
            machinePressActive=false;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            invalidate();
        }
    };



    // Render-only interpolation. Never serialized and never changes simulation.
    // An arrival animates from the sending tile to this tile over one belt tick;
    // if blocked, the number rests at its destination instead of jumping back.
    private static final class Travel {
        float fromX,fromY,start=-1f,duration=1f;
    }
    private static final class Node {
        final Travel lowerTravel=new Travel(),upperTravel=new Travel();
        int type,dir,value,item=EMPTY,a=EMPTY,b=EMPTY,upgrade=1,extracted;
        int crossDir=-1,crossItem=EMPTY; // Overpass or divider output channel.
        int storedCount=0; // A storage holds at most 10000 copies of one number.
        // Only the controller stores cargo. All other occupied tiles are PARTs.
        boolean multi=false;
        int rotation,originX,originY,ownerX,ownerY,port;
        float progress;
        Node(int type,int dir,int value) {
            this.type=type;this.dir=dir;this.value=value;
        }
    }

    public FactoryView(Context ctx) {
        super(ctx);
        density=getResources().getDisplayMetrics().density;
        prefs=ctx.getSharedPreferences("numera_world_v2",Context.MODE_PRIVATE);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        for(int i=0;i<upgradeLevels.length;i++)upgradeBanks.add(new HashMap<>());
        load();
    }

    private static long id(int x,int y) {
        return (((long)x)<<32) | (y&0xffffffffL);
    }
    private void put(int x,int y,int type,int dir,int value) {
        cells.put(id(x,y),new Node(type,dir,value));
    }
    private Node get(int x,int y) { return cells.get(id(x,y)); }

    // Multi-tile occupancy and port ownership, backed by MachineLayout.
    private Node machineAt(int x,int y) {
        Node tile=get(x,y);
        if(tile==null)return null;
        if(tile.multi)return tile;
        if(tile.type!=PART)return null;
        Node owner=get(tile.ownerX,tile.ownerY);
        return owner!=null&&owner.multi?owner:null;
    }
    private int machinePort(int x,int y) {
        Node tile=get(x,y);
        return tile!=null&&(tile.multi||tile.type==PART)?tile.port:0;
    }
    private void removeBuilding(int x,int y) {
        Node owner=machineAt(x,y);
        if(owner==null) {cells.remove(id(x,y));return;}
        MachineLayout.Shape shape=MachineLayout.shape(owner.type,owner.rotation);
        if(shape==null)return;
        for(MachineLayout.Cell part:shape.cells)
            cells.remove(id(owner.originX+part.x,owner.originY+part.y));
    }
    private boolean canBuild(int x,int y,int type,int rotation) {
        MachineLayout.Shape shape=MachineLayout.shape(type,rotation);
        if(shape==null)return false;
        for(MachineLayout.Cell part:shape.cells) {
            int xx=x+part.x,yy=y+part.y;
            if(!within(xx,yy)||isHub(xx,yy)||get(xx,yy)!=null||
               depositUnder(xx,yy)>0)return false;
        }
        return true;
    }
    private boolean putMachine(int x,int y,int type,int rotation) {
        if(!canBuild(x,y,type,rotation))return false;
        MachineLayout.Shape shape=MachineLayout.shape(type,rotation);
        MachineLayout.Cell first=shape.cells[0];
        int ax=x+first.x,ay=y+first.y;
        Node controller=new Node(type,0,type==STORAGE?EMPTY:0);
        controller.multi=true;controller.rotation=rotation&3;
        controller.originX=x;controller.originY=y;
        controller.ownerX=ax;controller.ownerY=ay;controller.port=first.port;
        cells.put(id(ax,ay),controller);
        for(int i=1;i<shape.cells.length;i++) {
            MachineLayout.Cell cell=shape.cells[i];
            Node part=new Node(PART,0,0);
            part.ownerX=ax;part.ownerY=ay;part.port=cell.port;
            cells.put(id(x+cell.x,y+cell.y),part);
        }
        return true;
    }
    /**
     * Atomically relocate or rotate an occupied multi-tile machine. Allow
     * overlaps with its OWN previous tiles, reject any other objects, and
     * preserve the same controller instance with all A/B/buffer contents.
     */
    private boolean canReconfigure(Node owner,int newX,int newY,int rotation) {
        if(owner==null||!owner.multi)return false;
        MachineLayout.Shape shape=MachineLayout.shape(owner.type,rotation);
        if(shape==null)return false;
        for(MachineLayout.Cell part:shape.cells) {
            int xx=newX+part.x,yy=newY+part.y;
            if(!within(xx,yy)||isHub(xx,yy)||depositUnder(xx,yy)>0)return false;
            Node occupied=get(xx,yy);
            if(occupied!=null&&machineAt(xx,yy)!=owner)return false;
        }
        return true;
    }
    private boolean relocateMachine(Node owner,int newX,int newY,int rotation) {
        if(!canReconfigure(owner,newX,newY,rotation))return false;
        MachineLayout.Shape old=MachineLayout.shape(owner.type,owner.rotation);
        for(MachineLayout.Cell cell:old.cells)
            cells.remove(id(owner.originX+cell.x,owner.originY+cell.y));
        MachineLayout.Shape shape=MachineLayout.shape(owner.type,rotation);
        MachineLayout.Cell first=shape.cells[0];
        int anchorX=newX+first.x,anchorY=newY+first.y;
        owner.originX=newX;owner.originY=newY;owner.rotation=rotation&3;
        owner.ownerX=anchorX;owner.ownerY=anchorY;owner.port=first.port;
        cells.put(id(anchorX,anchorY),owner);
        for(int i=1;i<shape.cells.length;i++) {
            MachineLayout.Cell cell=shape.cells[i];
            Node part=new Node(PART,0,0);
            part.ownerX=anchorX;part.ownerY=anchorY;part.port=cell.port;
            cells.put(id(newX+cell.x,newY+cell.y),part);
        }
        save();
        return true;
    }
    // Floating rotation control is anchored above the selected building.
    // It never takes space from the bottom toolbar or belt drawing area.
    private float rotateIconX() {
        if(focusedMachine==null)return -1000f;
        MachineLayout.Shape shape=MachineLayout.shape(
            focusedMachine.type,focusedMachine.rotation);
        int gx=editDragGesture?editTargetX:focusedMachine.originX;
        float x=panX+(gx+shape.width*.5f)*sz();
        return Math.max(24*density,Math.min(getWidth()-24*density,x));
    }
    private float rotateIconY() {
        if(focusedMachine==null)return -1000f;
        int gy=editDragGesture?editTargetY:focusedMachine.originY;
        float y=panY+gy*sz()-28*density;
        return Math.max(151*density,
            Math.min(getHeight()-TOOLBAR_DP*density-26*density,y));
    }
    private boolean hitRotationIcon(float x,float y) {
        if(focusedMachine==null)return false;
        return Math.hypot(x-rotateIconX(),y-rotateIconY())<=27*density;
    }
    private void setMachineMessage(String msg) {
        machineEditMessage=msg;
        machineEditMessageUntil=clock+2f;
    }
    private void drawMachineRotateIcon(Canvas c) {
        if(focusedMachine==null)return;
        float d=density,x=rotateIconX(),y=rotateIconY();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xff0d2836);
        c.drawCircle(x,y,24*d,p);
        p.setColor(0xff2b7680);
        c.drawCircle(x,y,20*d,p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f,2*d));
        p.setColor(0xffc8fff4);
        c.drawCircle(x,y,19*d,p);
        p.setStyle(Paint.Style.FILL);
        label(c,"↻",x,y+10*d,29*d,0xfff0fff8,true,Paint.Align.CENTER);
        if(clock<machineEditMessageUntil) {
            label(c,machineEditMessage,x,Math.max(133*density,y-33*d),
                  11*d,0xffffc99a,true,Paint.Align.CENTER);
        }
    }
    private void drawMachineEditOutline(Canvas c) {
        if(focusedMachine==null||!editDragGesture)return;
        Node m=focusedMachine;
        MachineLayout.Shape shape=MachineLayout.shape(m.type,m.rotation);
        if(shape==null)return;
        float s=sz(),ox=panX+editTargetX*s,oy=panY+editTargetY*s;
        Path silhouette=machineSilhouette(m.type,m.rotation,ox,oy,s);
        boolean available=canReconfigure(m,editTargetX,editTargetY,m.rotation);
        // Drag the actual rounded machine skin, not just a selection square.
        drawMultiMachineAt(c,m,editTargetX,editTargetY);
        p.setStyle(Paint.Style.FILL);
        p.setColor(available?0x2642dbce:0x66ed6c76);
        c.drawPath(silhouette,p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2*density,s*.07f));
        p.setColor(available?0xff74eee2:0xfffa9292);
        c.drawPath(silhouette,p);
        p.setStyle(Paint.Style.FILL);
    }

    private void seed() {
        cells.clear();
        level=1;goal=1;required=10;delivered=0;allDelivered=0;
        for(int i=0;i<upgradeLevels.length;i++){upgradeLevels[i]=1;upgradeBanks.get(i).clear();}
        // Blank factory: only the protected 6x6 collection hub exists.
        for(int y=HUB_Y;y<HUB_Y+HUB_SIZE;y++)
            for(int x=HUB_X;x<HUB_X+HUB_SIZE;x++) put(x,y,HUB,0,0);
    }
    private boolean isHub(int x,int y) {
        return x>=HUB_X && x<HUB_X+HUB_SIZE && y>=HUB_Y && y<HUB_Y+HUB_SIZE;
    }
    private int terrainHash(int gx,int gy,int slot) {
        int h=gx*0x632be5ab ^ gy*0x85157af5 ^ slot*0x6c8e9cf5 ^ 0x5a726194;
        h^=h>>>16;h*=0x7feb352d;h^=h>>>15;h*=0x846ca68b;
        return (h^(h>>>16))&Integer.MAX_VALUE;
    }
    private int[] depositCandidate(int gx,int gy,int slot) {
        int h=terrainHash(gx,gy,slot);
        if(slot==0 && h%100>=92)return null;
        if(slot==1 && h%100>=38)return null;
        int x=gx*DEPOSIT_REGION+5+Math.floorMod(h>>>6,36);
        int y=gy*DEPOSIT_REGION+5+Math.floorMod(h>>>14,36);
        return new int[]{x,y,h};
    }
    private int numberForDeposit(int x,int y,int h) {
        float dist=(float)Math.hypot(x-(HUB_X+3),y-(HUB_Y+3));
        if(dist<85f) {
            int[] common={1,1,1,1,1,1,1,1,2,2,3,4,8};
            return common[Math.floorMod(h>>>21,common.length)];
        }
        if(dist<240f) {
            int[] mid={1,2,3,4,5,6,7,8,9,10,12,15};
            return mid[Math.floorMod(h>>>19,mid.length)];
        }
        return 1+Math.floorMod(h>>>19,Math.min(999,15+(int)(dist/90f)));
    }
    private boolean depositAccepted(int gx,int gy,int slot,int[] c) {
        if(c==null)return false;
        int x=c[0],y=c[1];
        int hubDx=x-(HUB_X+3),hubDy=y-(HUB_Y+3);
        if(hubDx*hubDx+hubDy*hubDy<23*23)return false;
        for(int[] fixed:START_DEPOSITS) {
            int dx=x-fixed[0],dy=y-fixed[1];
            if(dx*dx+dy*dy<MIN_DEPOSIT_SEPARATION*MIN_DEPOSIT_SEPARATION)
                return false;
        }
        // Local hash-priority filter removes regularly spaced clumps.
        for(int ry=gy-1;ry<=gy+1;ry++)
            for(int rx=gx-1;rx<=gx+1;rx++)
                for(int t=0;t<2;t++) {
                    if(rx==gx&&ry==gy&&t==slot)continue;
                    int[] other=depositCandidate(rx,ry,t);
                    if(other==null)continue;
                    int dx=x-other[0],dy=y-other[1];
                    if(dx*dx+dy*dy<MIN_DEPOSIT_SEPARATION*MIN_DEPOSIT_SEPARATION &&
                       (other[2]>c[2] ||
                        (other[2]==c[2]&&(rx<gx||(rx==gx&&ry<gy)))))
                        return false;
                }
        return true;
    }
    private int depositValue(int x,int y) {
        for(int[] field:START_DEPOSITS)
            if(field[0]==x&&field[1]==y)return field[2];
        int gx=Math.floorDiv(x,DEPOSIT_REGION);
        int gy=Math.floorDiv(y,DEPOSIT_REGION);
        for(int t=0;t<2;t++) {
            int[] c=depositCandidate(gx,gy,t);
            if(c!=null&&c[0]==x&&c[1]==y&&depositAccepted(gx,gy,t,c))
                return numberForDeposit(x,y,c[2]);
        }
        return -1;
    }
    private int depositUnder(int x,int y) {
        for(int dy=-1;dy<=1;dy++)
            for(int dx=-1;dx<=1;dx++)
                if(depositValue(x+dx,y+dy)>0)return 1;
        return -1;
    }
    // Every visible cell of a 3x3 deposit is a valid touch target. Its
    // extractor still lives at the single canonical centre tile.
    private Point depositCenterAt(int x,int y) {
        for(int dy=-1;dy<=1;dy++)
            for(int dx=-1;dx<=1;dx++)
                if(depositValue(x+dx,y+dy)>0)
                    return new Point(x+dx,y+dy);
        return null;
    }
    private void drawDeposit(Canvas c,int x,int y,int value) {
        float s=sz(),left=panX+(x-1)*s,top=panY+(y-1)*s;
        if(left>getWidth()+s||top>getHeight()+s||left+3*s<0||top+3*s<0)return;
        box(c,left+s*.035f,top+s*.035f,left+s*2.965f,top+s*2.965f,
                s*.30f,0xff5c452c);
        box(c,left+s*.15f,top+s*.15f,left+s*2.85f,top+s*2.85f,
                s*.24f,0xffb78549);
        p.setColor(0x77ffe3a6);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f,s*.05f));
        c.drawRoundRect(left+s*.18f,top+s*.18f,left+s*2.82f,top+s*2.82f,
                s*.22f,s*.22f,p);p.setStyle(Paint.Style.FILL);
        label(c,Integer.toString(value),left+s*1.5f,top+s*1.83f,
              s*1.1f,0xff31291f,true,Paint.Align.CENTER);
    }
    private void drawDeposits(Canvas c) {
        for(int[] field:START_DEPOSITS)
            drawDeposit(c,field[0],field[1],field[2]);
        int minX=Math.floorDiv(gridX(0)-DEPOSIT_REGION,DEPOSIT_REGION);
        int maxX=Math.floorDiv(gridX(getWidth())+DEPOSIT_REGION,DEPOSIT_REGION);
        int minY=Math.floorDiv(gridY(0)-DEPOSIT_REGION,DEPOSIT_REGION);
        int maxY=Math.floorDiv(gridY(getHeight())+DEPOSIT_REGION,DEPOSIT_REGION);
        minX=Math.max(-116,minX);maxX=Math.min(116,maxX);
        minY=Math.max(-116,minY);maxY=Math.min(116,maxY);
        for(int gy=minY;gy<=maxY;gy++)
            for(int gx=minX;gx<=maxX;gx++)
                for(int t=0;t<2;t++) {
                    int[] cnd=depositCandidate(gx,gy,t);
                    if(depositAccepted(gx,gy,t,cnd))
                        drawDeposit(c,cnd[0],cnd[1],
                            numberForDeposit(cnd[0],cnd[1],cnd[2]));
                }
    }
    private void drawHub(Canvas c) {
        float s=sz(),left=panX+HUB_X*s,top=panY+HUB_Y*s,side=HUB_SIZE*s;
        if(left>getWidth()||top>getHeight()||left+side<0||top+side<0)return;
        // Flat original game-style hub: no glow or size/debug annotation.
        box(c,left,top,left+side,top+side,s*.35f,0xff16475b);
        box(c,left+s*.17f,top+s*.17f,left+side-s*.17f,top+side-s*.17f,
                s*.32f,0xff2a9c9a);
        box(c,left+s*.35f,top+s*.35f,left+side-s*.35f,top+side-s*.35f,
                s*.27f,0xff142e40);
        label(c,Integer.toString(goal),left+side*.5f,top+side*.52f,
             s*1.9f,0xfff2fff5,true,Paint.Align.CENTER);
        label(c,delivered+" / "+required,left+side*.5f,top+side*.72f,
             s*.58f,0xffa9e8dc,true,Paint.Align.CENTER);
    }
    private void load() {
        String raw=prefs.getString("world",null);
        if(raw==null) { seed();return; }
        try {
            String[] parts=raw.split("#",2);
            String[] state=parts[0].split(",");
            level=Integer.parseInt(state[0]);
            goal=Integer.parseInt(state[1]);
            required=Integer.parseInt(state[2]);
            delivered=Integer.parseInt(state[3]);
            allDelivered=state.length>4?Integer.parseInt(state[4]):delivered;
            cells.clear();
            for(String record:parts[1].split(";")) {
                if(record.isEmpty()) continue;
                String[] v=record.split(",");
                int xx=Integer.parseInt(v[0]),yy=Integer.parseInt(v[1]);
                put(xx,yy,Integer.parseInt(v[2]),Integer.parseInt(v[3]),Integer.parseInt(v[4]));
                if(v.length>5) get(xx,yy).upgrade=Integer.parseInt(v[5]);
                if(v.length>6) get(xx,yy).crossDir=Integer.parseInt(v[6]);
                if(v.length>7) get(xx,yy).storedCount=Integer.parseInt(v[7]);
                if(v.length>14) {
                    Node n=get(xx,yy);
                    n.multi=Integer.parseInt(v[8])!=0;
                    n.rotation=Integer.parseInt(v[9]);
                    n.originX=Integer.parseInt(v[10]);
                    n.originY=Integer.parseInt(v[11]);
                    n.ownerX=Integer.parseInt(v[12]);
                    n.ownerY=Integer.parseInt(v[13]);
                    n.port=Integer.parseInt(v[14]);
                }
            }
            if(cells.isEmpty()) seed();
            for(int y=HUB_Y;y<HUB_Y+HUB_SIZE;y++)
                for(int x=HUB_X;x<HUB_X+HUB_SIZE;x++)put(x,y,HUB,0,0);
            loadUpgrades();
        } catch(Exception ex) {seed();}
    }
    private void loadUpgrades() {
        String raw=prefs.getString("upgrades",null);
        if(raw==null)return;
        try {
            String[] parts=raw.split("#",2);
            String[] levels=parts[0].split(",");
            for(int i=0;i<Math.min(upgradeLevels.length,levels.length);i++)
                upgradeLevels[i]=Math.max(1,Integer.parseInt(levels[i]));
            String[] banks=parts[1].split(";",-1);
            for(int i=0;i<Math.min(upgradeLevels.length,banks.length);i++) {
                upgradeBanks.get(i).clear();
                for(String entry:banks[i].split(",")) {
                    if(entry.isEmpty())continue;
                    String[] pair=entry.split(":");
                    upgradeBanks.get(i).put(Integer.parseInt(pair[0]),Long.parseLong(pair[1]));
                }
            }
        }catch(Exception ex){
            for(int i=0;i<upgradeLevels.length;i++){upgradeLevels[i]=1;upgradeBanks.get(i).clear();}
        }
    }
    private String saveUpgrades() {
        StringBuilder b=new StringBuilder();
        for(int i=0;i<upgradeLevels.length;i++) {
            if(i>0)b.append(',');
            b.append(upgradeLevels[i]);
        }
        b.append('#');
        for(int i=0;i<upgradeLevels.length;i++) {
            if(i>0)b.append(';');
            for(Map.Entry<Integer,Long> e:upgradeBanks.get(i).entrySet())
                b.append(e.getKey()).append(':').append(e.getValue()).append(',');
        }
        return b.toString();
    }
    private void recordNumber(int number) {
        for(Map<Integer,Long> bank:upgradeBanks)bank.put(number,bank.getOrDefault(number,0L)+1);
    }
    private boolean operatorUnlocked(int type) {
        for(int i=0;i<OPERATORS.length;i++)
            if(OPERATORS[i]==type)return level>=OP_LEVELS[i];
        return false;
    }
    private boolean machineMenuVisible() {return selected==2||selected==3;}
    private void drawMachineMenu(Canvas c) {
        float d=density,w=getWidth(),top=getHeight()-(TOOLBAR_DP+BELT_MENU_DP)*d;
        box(c,6*d,top,w-6*d,top+BELT_MENU_DP*d,12*d,0xff173448);
        float cell=w/(OPERATORS.length+1);
        for(int i=0;i<OPERATORS.length;i++) {
            float left=cell*i,mid=left+cell*.5f;
            boolean unlocked=operatorUnlocked(OPERATORS[i]);
            if(operatorChoice==OPERATORS[i]&&unlocked)
                box(c,left+3*d,top+3*d,left+cell-3*d,top+41*d,9*d,0xff297679);
            label(c,OP_SYMBOLS[i],mid,top+22*d,19*d,
                unlocked?0xffe8fff9:0xff68818d,true,Paint.Align.CENTER);
            label(c,unlocked?"":"ур."+OP_LEVELS[i],mid,top+37*d,8*d,
                0xffffbd85,false,Paint.Align.CENTER);
        }
        float mid=w-cell*.5f;
        box(c,w-cell+3*d,top+3*d,w-3*d,top+41*d,9*d,0xff285268);
        label(c,"↻",mid,top+24*d,21*d,0xffdafff5,true,Paint.Align.CENTER);
        label(c,Integer.toString(machineRotation*90)+"°",mid,top+37*d,9*d,
              0xff9dd7d3,false,Paint.Align.CENTER);
    }
    private boolean upgradeUnlocked(int kind) {
        return kind>=0&&kind<2||kind==2||kind==3&&level>=7||
               kind==4&&level>=13||kind==5&&level>=19;
    }
    private int[][] requirements(int kind) {
        int step=upgradeLevels[kind]-1;
        int[][][] track=kind<4?UPGRADE_REQ[kind]:ADV_UPGRADE_REQ[kind-4];
        return step<track.length?track[step]:new int[0][];
    }
    private boolean buyUpgrade(int kind) {
        if(!upgradeUnlocked(kind))return false;
        int[][] req=requirements(kind);
        if(req.length==0)return false;
        Map<Integer,Long> bank=upgradeBanks.get(kind);
        for(int[] row:req)if(bank.getOrDefault(row[0],0L)<row[1])return false;
        for(int[] row:req)bank.put(row[0],bank.getOrDefault(row[0],0L)-row[1]);
        upgradeLevels[kind]++;
        save();
        return true;
    }
    private float beltSpeed() {
        int level=upgradeLevels[0];
        return level<=8?level:8f+(level-8)*.1f;
    }
    private float operatorSpeed(int type) {
        int group=type==ADD?2:type==MULTIPLY?3:
                  type==SUBTRACT||type==DIVIDE?4:5;
        int stage=Math.max(1,upgradeLevels[group]);
        float[] add={.25f,1f/3f,.4f,.5f,2f/3f,1f,1.5f,2f};
        float[] other={.2f,.3f,.4f,.5f,.6f,.7f,.8f,1f};
        return stage<=8?(group==2?add[stage-1]:other[stage-1]):
               (group==2?2f:1f)+(stage-8)*.02f;
    }
    private int extractionMax() {
        // Extraction rewards apply after the matching level is completed.
        if(level<=3)return 1;
        if(level<=5)return 2;
        if(level==6)return 3;
        if(level<=8)return 4;
        if(level==9)return 5;
        if(level<=11)return 6;
        if(level==12)return 7;
        if(level<=14)return 8;
        if(level==15)return 9;
        if(level<=17)return 11;
        if(level==18)return 12;
        if(level<=20)return 13;
        if(level==21)return 14;
        if(level<=23)return 15;
        return Math.min(999,level-8);
    }
    public void save() {
        StringBuilder b=new StringBuilder();
        b.append(level).append(',').append(goal).append(',')
            .append(required).append(',').append(delivered).append(',')
            .append(allDelivered).append('#');
        for(Map.Entry<Long,Node> entry:cells.entrySet()) {
            long key=entry.getKey();
            Node n=entry.getValue();
            b.append((int)(key>>32)).append(',').append((int)key).append(',')
                .append(n.type).append(',').append(n.dir).append(',')
                .append(n.value).append(',').append(n.upgrade).append(',')
                 .append(n.crossDir).append(',').append(n.storedCount).append(',')
                 .append(n.multi?1:0).append(',').append(n.rotation).append(',')
                 .append(n.originX).append(',').append(n.originY).append(',')
                 .append(n.ownerX).append(',').append(n.ownerY).append(',')
                 .append(n.port).append(';');
        }
        prefs.edit().putString("world",b.toString())
            .putString("upgrades",saveUpgrades()).apply();
    }

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        super.onSizeChanged(w,h,oldW,oldH);
        float oldTile=tile;
        tile=w/11.9f;
        if(!initialized) {
            panX=w*.52f-12.5f*tile;
            panY=h*.53f-12.5f*tile;
            initialized=true;
        } else if(oldTile>0) {
            panX+=(w-oldW)*.5f;
            panY+=(h-oldH)*.5f;
        }
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now=System.nanoTime();
        if(lastFrame!=0) {
            float dt=Math.min(.05f,(now-lastFrame)/1_000_000_000f);
            clock+=dt;flash=Math.max(0,flash-dt*1.4f);
            for(Node n:cells.values()) {
                if(n.type==ADD||n.type==MULTIPLY||n.type==SUBTRACT||
                   n.type==DIVIDE||n.type==EXPONENT) {
                    if(n.a!=EMPTY&&n.b!=EMPTY&&n.item==EMPTY)
                        n.progress=Math.min(2f,n.progress+dt*operatorSpeed(n.type));
                    else n.progress=0f;
                }
            }
            updateEdgeScroll(dt);
            stepAccumulator+=dt;
            float stepInterval=1f/beltSpeed();
            int ticks=0;
            while(stepAccumulator>=stepInterval && ticks++<4) {
                stepAccumulator-=stepInterval;
                step();
            }
        }
        lastFrame=now;
        c.drawColor(BG);
        drawGrid(c);
        drawDeposits(c);
        drawHub(c);
        float hudH=124*density;
        float barY=getHeight()-94*density;
        c.save();
        c.clipRect(0,hudH,getWidth(),barY);
        for(Map.Entry<Long,Node> entry:cells.entrySet()) {
            long key=entry.getKey();
            if(entry.getValue().type!=HUB &&
               !(editDragGesture&&entry.getValue()==focusedMachine))
                drawNode(c,(int)(key>>32),(int)key,entry.getValue());
        }
        // Render moving tokens after every belt and machine, not under their
        // neighbours. The underlying integer-grid logistics stay unchanged.
        for(Map.Entry<Long,Node> entry:cells.entrySet()) {
            long key=entry.getKey();
            Node n=entry.getValue();
            if(n.item!=EMPTY&&!n.multi)
                drawTravellingNumber(c,(int)(key>>32),(int)key,n,false);
            if(n.crossItem!=EMPTY&&!n.multi && (n.type==BRIDGE||n.type==DIVIDE))
                drawTravellingNumber(c,(int)(key>>32),(int)key,n,true);
        }
        drawRoutePreview(c);
        drawSelection(c);
        drawMachineEditOutline(c);
        drawMachineRotateIcon(c);
        c.restore();
        drawHud(c);
        drawToolbar(c);
        if(selected==0)drawBeltMenu(c);
        if(selected==5)drawSelectionMenu(c);
        if(machineMenuVisible())drawMachineMenu(c);
        if(upgradePanel)drawUpgradePanel(c);
        postInvalidateOnAnimation();
    }

    private void deliverToHub(int number) {
        recordNumber(number);
        allDelivered++;
        if(number==goal) {
            delivered++;
            flash=1f;
            if(delivered>=required) {
                level++;
                int index=level-1;
                if(index<GOAL_SEQUENCE.length) {
                    goal=GOAL_SEQUENCE[index];
                    required=GOAL_COUNTS[index];
                } else {
                    // Extension after hand-authored progression; do not loop to level 1.
                    goal=(int)Math.min(999999999L,(long)goal+Math.max(100,goal/3));
                    required=Math.min(20000,required+500);
                }
                delivered=0;
            }
        }
        if((allDelivered&31)==0)save();
    }

    private void markTravel(Node target,boolean upper,int fromX,int fromY) {
        Travel travel=upper?target.upperTravel:target.lowerTravel;
        travel.fromX=fromX;
        travel.fromY=fromY;
        travel.start=clock;
        travel.duration=Math.max(.025f,1f/beltSpeed());
    }

    // A transfer is a reservation for a single cargo lane, not an immediate
    // mutation. All transfers are committed together after dependency checks.
    private static final class Transfer {
        long fromKey,toKey;
        Node from,to;
        int number,fromX,fromY,port; // 0=lower, 1=upper, 2=hub, 3=A, 4=B, 5=storage
        boolean fromUpper;
        Transfer(long fromKey,Node from,boolean upper,int fromX,int fromY,
                 long toKey,Node to,int number,int port) {
            this.fromKey=fromKey;this.from=from;this.fromUpper=upper;
            this.fromX=fromX;this.fromY=fromY;
            this.toKey=toKey;this.to=to;this.number=number;this.port=port;
        }
    }

    private void offerTransfer(long fromKey,Node source,boolean upper,
                               int x,int y,ArrayList<Transfer> proposals,
                               HashSet<Long> lowerClaims,HashSet<Long> upperClaims,
                               HashSet<Long> aClaims,HashSet<Long> bClaims,
                               HashSet<Long> storageClaims) {
        offerTransferFrom(fromKey,source,upper,x,y,
            upper?source.crossDir:source.dir,proposals,
            lowerClaims,upperClaims,aClaims,bClaims,storageClaims);
    }

    private void offerTransferFrom(long fromKey,Node source,boolean upper,
                                   int x,int y,int dir,ArrayList<Transfer> proposals,
                                   HashSet<Long> lowerClaims,HashSet<Long> upperClaims,
                                   HashSet<Long> aClaims,HashSet<Long> bClaims,
                                   HashSet<Long> storageClaims) {
        int number=upper?source.crossItem:source.item;
        if(number==EMPTY||dir<0||dir>3)return;
        int nx=x+DX[dir],ny=y+DY[dir];
        long key=id(nx,ny);
        Node owner=machineAt(nx,ny);
        Node target=owner==null?get(nx,ny):owner;
        if(target==null)return;
        int machineInput=owner==null?0:machinePort(nx,ny);
        long claimKey=owner==null?key:id(owner.ownerX,owner.ownerY);
        int port=-1;
        if(target.type==HUB) {
            port=2;
        } else if(target.type==BELT) {
            if(!lowerClaims.add(key))return;
            port=0;
        } else if(target.type==BRIDGE) {
            if(target.dir==dir && lowerClaims.add(key))port=0;
            else if(target.crossDir==dir && upperClaims.add(key))port=1;
            if(port<0)return;
        } else if(target.type==STORAGE) {
            if(owner!=null && machineInput!=MachineLayout.STORAGE)return;
            if(target.storedCount>=10000 ||
                (target.storedCount>0&&target.value!=number) ||
                !storageClaims.add(claimKey))return;
            port=5;
        } else if(target.type==ADD||target.type==MULTIPLY||
                  target.type==SUBTRACT||target.type==DIVIDE||
                  target.type==EXPONENT) {
            if(owner!=null) {
                if(machineInput==MachineLayout.A &&
                    target.a==EMPTY && aClaims.add(claimKey))port=3;
                else if(machineInput==MachineLayout.B &&
                    target.b==EMPTY && bClaims.add(claimKey))port=4;
            } else {
                // Older saved one-cell operators keep their old behaviour.
                if(y<ny && target.a==EMPTY && aClaims.add(claimKey))port=3;
                else if(y>ny && target.b==EMPTY && bClaims.add(claimKey))port=4;
                else if(target.a==EMPTY && aClaims.add(claimKey))port=3;
                else if(target.b==EMPTY && bClaims.add(claimKey))port=4;
            }
            if(port<0)return;
        } else return;
        proposals.add(new Transfer(fromKey,source,upper,x,y,claimKey,target,number,port));
    }

    private void offerMachineOutput(long key,Node machine,boolean remainder,
                                     ArrayList<Transfer> proposals,
                                     HashSet<Long> lowerClaims,HashSet<Long> upperClaims,
                                     HashSet<Long> aClaims,HashSet<Long> bClaims,
                                     HashSet<Long> storageClaims) {
        if((remainder?machine.crossItem:machine.item)==EMPTY)return;
        MachineLayout.Shape shape=MachineLayout.shape(machine.type,machine.rotation);
        if(shape==null)return;
        int wanted=remainder?MachineLayout.REMAINDER:MachineLayout.OUTPUT;
        int preferred[]={0,1,3,2}; // right, down, up, left
        for(MachineLayout.Cell part:shape.cells) {
            if(part.port!=wanted)continue;
            int px=machine.originX+part.x,py=machine.originY+part.y;
            for(int dir:preferred) {
                int tx=px+DX[dir],ty=py+DY[dir];
                if(machineAt(tx,ty)==machine)continue; // inner face
                int before=proposals.size();
                offerTransferFrom(key,machine,remainder,px,py,dir,proposals,
                                  lowerClaims,upperClaims,aClaims,bClaims,storageClaims);
                if(proposals.size()>before)return;
            }
        }
    }

    private void transportTick() {
        // Deterministic proposal order, unlike hash iteration. All cargo
        // positions and occupancy refer to the SAME start-of-tick snapshot.
        ArrayList<Long> keys=new ArrayList<>(cells.keySet());
        Collections.sort(keys);
        ArrayList<Transfer> proposals=new ArrayList<>();
        HashSet<Long> lowerClaims=new HashSet<>(),upperClaims=new HashSet<>();
        HashSet<Long> aClaims=new HashSet<>(),bClaims=new HashSet<>();
        HashSet<Long> storageClaims=new HashSet<>();
        for(long key:keys) {
            Node n=cells.get(key);
            if(n==null||n.type==SOURCE||n.type==HUB||
               n.type==STORAGE||n.type==PART)continue;
            int x=(int)(key>>32),y=(int)key;
            if(n.multi) {
                // Cargo leaves ONLY via the actual O / R port tiles.
                offerMachineOutput(key,n,false,proposals,lowerClaims,upperClaims,
                                   aClaims,bClaims,storageClaims);
                if(n.type==DIVIDE)
                    offerMachineOutput(key,n,true,proposals,lowerClaims,upperClaims,
                                       aClaims,bClaims,storageClaims);
            } else {
                if(n.item!=EMPTY)
                    offerTransfer(key,n,false,x,y,proposals,lowerClaims,upperClaims,
                                  aClaims,bClaims,storageClaims);
                if((n.type==BRIDGE||n.type==DIVIDE)&&n.crossItem!=EMPTY)
                    offerTransfer(key,n,true,x,y,proposals,lowerClaims,upperClaims,
                                  aClaims,bClaims,storageClaims);
            }
        }
        // Each target has at most one reservation on each bridge lane.
        // Store the incoming edge, not the outgoing one, so vacancy can
        // propagate backwards from the exit to the start of a full belt.
        HashMap<Long,Transfer> incomingLower=new HashMap<>(),incomingUpper=new HashMap<>();
        HashSet<Transfer> movable=new HashSet<>();
        ArrayList<Transfer> queue=new ArrayList<>();
        for(Transfer t:proposals) {
            if(t.port==0)incomingLower.put(t.toKey,t);
            else if(t.port==1)incomingUpper.put(t.toKey,t);
            if(t.port>=2 || (t.port==0 && t.to.item==EMPTY) ||
               (t.port==1 && t.to.crossItem==EMPTY)) {
                movable.add(t);
                queue.add(t);
            }
        }
        // Linear-time dependency walk: moving from a cell frees that cell's
        // lane for its upstream neighbour. No hash-order stalls and no
        // repeated whole-factory scans for long chains.
        for(int i=0;i<queue.size();i++) {
            Transfer leaving=queue.get(i);
            Transfer upstream=leaving.fromUpper?
                incomingUpper.get(leaving.fromKey):incomingLower.get(leaving.fromKey);
            if(upstream!=null && movable.add(upstream))queue.add(upstream);
        }
        // Clear only approved sources. This must happen before writing
        // destinations or queued numbers could be overwritten or duplicated.
        for(Transfer t:proposals) {
            if(!movable.contains(t))continue;
            if(t.fromUpper)t.from.crossItem=EMPTY;
            else t.from.item=EMPTY;
        }
        int deliveredThisTick=0;
        for(Transfer t:proposals) {
            if(!movable.contains(t))continue;
            switch(t.port) {
                case 0:
                    t.to.item=t.number;
                    markTravel(t.to,false,t.fromX,t.fromY);
                    break;
                case 1:
                    t.to.crossItem=t.number;
                    markTravel(t.to,true,t.fromX,t.fromY);
                    break;
                case 2:
                    deliverToHub(t.number);
                    deliveredThisTick++;
                    break;
                case 3: t.to.a=t.number;break;
                case 4: t.to.b=t.number;break;
                case 5:
                    if(t.to.storedCount==0)t.to.value=t.number;
                    t.to.storedCount++;
                    break;
                default:break;
            }
        }
    }

    private void step() {
        transportTick();
        for(Map.Entry<Long,Node> entry:cells.entrySet()) {
            Node n=entry.getValue();
            if(n.type!=SOURCE)continue;
            int x=(int)(entry.getKey()>>32),y=(int)(long)entry.getKey();
            // One valid tap creates exactly one miner, with twelve potential
            // perimeter connections and upgrade-limited total throughput.
            int maxOutputs=Math.min(12,upgradeLevels[1]);
            int produced=0;
            int start=Math.floorMod(n.extracted,MINE_OUTPUTS.length);
            for(int scan=0;scan<MINE_OUTPUTS.length && produced<maxOutputs;scan++) {
                int i=(start+scan)%MINE_OUTPUTS.length;
                int xx=x+MINE_OUTPUTS[i][0],yy=y+MINE_OUTPUTS[i][1];
                Node out=get(xx,yy);
                if(out!=null&&out.type==BELT&&out.item==EMPTY) {
                    out.item=n.value;
                    markTravel(out,false,x,y);
                    produced++;
                }
            }
            n.extracted+=Math.max(1,produced);
        }
        for(Node n:cells.values()) {
            if((n.type==ADD || n.type==MULTIPLY || n.type==SUBTRACT ||
                n.type==DIVIDE || n.type==EXPONENT) &&
               n.a!=EMPTY && n.b!=EMPTY && n.item==EMPTY &&
               (n.type!=DIVIDE || n.crossItem==EMPTY) &&
               n.progress>=1f) {
                n.progress=0f;
                long result;
                switch(n.type) {
                    case ADD: result=(long)n.a+n.b;break;
                    case MULTIPLY: result=(long)n.a*n.b;break;
                    case SUBTRACT: result=(long)n.a-n.b;break;
                    case DIVIDE:
                        if(n.b==0) {n.b=EMPTY;continue;}
                        result=n.a/n.b;
                        n.crossDir=(n.dir+1)%4;
                        n.crossItem=n.a%n.b;
                        n.upperTravel.start=-1f;
                        break;
                    case EXPONENT:
                        if(n.b<0)result=0;
                        else {
                            long acc=1L,base=n.a;
                            int limit=Math.min(32,n.b);
                            for(int i=0;i<limit;i++) {
                                long absBase=Math.max(1L,Math.abs(base));
                                if(Math.abs(acc)>999999999L/absBase) {
                                    acc=(acc<0 ^ base<0)?-999999999L:999999999L;
                                    break;
                                }
                                acc*=base;
                            }
                            result=acc;
                            if(n.b>32 && Math.abs(base)>1)
                                result=(base<0 && (n.b&1)!=0)?-999999999L:999999999L;
                        }
                        break;
                    default:continue;
                }
                n.item=(int)Math.min(999999999L,Math.max(-999999999L,result));
                n.lowerTravel.start=-1f; // Produced at machine centre.
                n.a=EMPTY;n.b=EMPTY;
            }
        }
        for(Map.Entry<Long,Node> entry:cells.entrySet()) {
            Node n=entry.getValue();
            if(n.type!=STORAGE || n.storedCount<=0)continue;
            int x=(int)(entry.getKey()>>32),y=(int)(long)entry.getKey();
            if(n.multi) {
                MachineLayout.Shape shape=MachineLayout.shape(STORAGE,n.rotation);
                // The whole 3x3 perimeter is available, not just the anchor.
                for(MachineLayout.Cell cell:shape.cells) {
                    if(n.storedCount<=0)break;
                    int px=n.originX+cell.x,py=n.originY+cell.y;
                    for(int d=0;d<4 && n.storedCount>0;d++) {
                        int nx=px+DX[d],ny=py+DY[d];
                        if(machineAt(nx,ny)==n)continue;
                        Node out=get(nx,ny);
                        if(out!=null&&out.type==BELT&&out.dir==d&&
                           out.item==EMPTY) {
                            out.item=n.value;
                            markTravel(out,false,px,py);
                            n.storedCount--;
                        }
                    }
                }
            } else {
                // Compatibility for 1-cell storages from older saves.
                for(int d=0;d<4 && n.storedCount>0;d++) {
                    Node out=get(x+DX[d],y+DY[d]);
                    if(out!=null&&out.type==BELT&&out.dir==d&&out.item==EMPTY) {
                        out.item=n.value;
                        markTravel(out,false,x,y);
                        n.storedCount--;
                    }
                }
            }
            if(n.storedCount==0)n.value=EMPTY;
        }
    }

    private float sz() {return tile*zoom;}
    private float worldX(float sx) {return (sx-panX)/sz();}
    private float worldY(float sy) {return (sy-panY)/sz();}

    private void drawGrid(Canvas c) {
        float s=sz();
        if(s<1)return;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f,s*.017f));
        for(float x=panX%s;x<getWidth();x+=s) {
            p.setColor(0xff143044);
            c.drawLine(x,0,x,getHeight(),p);
        }
        for(float y=panY%s;y<getHeight();y+=s) {
            p.setColor(0xff143044);
            c.drawLine(0,y,getWidth(),y,p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void box(Canvas c,float l,float t,float r,float b,float radius,int color) {
        p.setStyle(Paint.Style.FILL);p.setColor(color);
        c.drawRoundRect(l,t,r,b,radius,radius,p);
    }
    private void label(Canvas c,String text,float x,float baseline,
                       float px,int color,boolean bold,Paint.Align align) {
        p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(color);
        p.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",
                                      bold?Typeface.BOLD:Typeface.NORMAL));
        p.setTextSize(px);p.setTextAlign(align);
        c.drawText(text,x,baseline,p);
    }
    private void token(Canvas c,float cx,float cy,float s,int number) {
        float width=Math.max(s*.31f,
                (String.valueOf(number).length()+1)*s*.17f);
        width=Math.min(width,s*.77f);
        box(c,cx-width*.5f,cy-s*.175f,cx+width*.5f,cy+s*.175f,
            s*.13f,0xffffd99d);
        label(c,Integer.toString(number),cx,cy+s*.085f,
              s*.23f,0xff242332,true,Paint.Align.CENTER);
    }


    // Two visible tiers at an overpass: each has its own directional arrows.
    private void drawBridgeLane(Canvas c,float cx,float cy,float s,
                                int dir,boolean raised) {
        c.save();
        c.rotate(dir*90f,cx,cy);
        box(c,cx-s*.49f,cy-s*(raised?.275f:.245f),
              cx+s*.49f,cy+s*(raised?.275f:.245f),
              s*.10f,raised?0xff224d61:0xff122a3d);
        box(c,cx-s*.47f,cy-s*.19f,cx+s*.47f,cy+s*.19f,
              s*.08f,raised?0xff237b89:0xff195263);
        p.setColor(raised?0xff7ef4dc:0xff47d1c8);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f,s*.028f));
        float shift=((clock*1.5f)%1f)*s*.24f;
        for(int a=-2;a<=2;a++) {
            float tx=cx+a*s*.24f+shift;
            if(tx>cx+s*.40f||tx<cx-s*.40f)continue;
            c.drawLine(tx-s*.058f,cy-s*.074f,tx,cy,p);
            c.drawLine(tx,cy,tx-s*.058f,cy+s*.074f,p);
        }
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    // A belt corner is a quadratic with start = centre - incoming*0.5,
    // control = centre and end = centre + outgoing*0.5.
    // One cargo position per tile is placed at the exact midpoint of that
    // SAME quadratic; two half-curves connect adjacent belt stations.
    // The previous arbitrary cubic bent the incoming number OUTSIDE the belt.
    private static boolean isElbow(int incoming,int outgoing) {
        return incoming!=outgoing && incoming!=(outgoing+2)%4;
    }

    private int beltIncomingDir(int x,int y,Node n) {
        int incoming=n.dir;
        for(int d=0;d<4;d++) {
            Node prev=get(x-DX[d],y-DY[d]);
            if(prev!=null && ((prev.type==BELT && prev.dir==d) ||
                (prev.type==BRIDGE && (prev.dir==d || prev.crossDir==d)))) {
                incoming=d;
                break;
            }
        }
        return incoming;
    }

    private static float quad(float a,float control,float end,float t) {
        float u=1f-t;
        return u*u*a+2f*u*t*control+t*t*end;
    }

    private static float lerp(float a,float b,float t) {
        return a+(b-a)*t;
    }

    /**
     * Each transfer runs from the source tile's centreline midpoint to the
     * destination's midpoint. A bent tile uses two halves of the very same
     * quadratic that draws its body, so numbers never cross outside the
     * turquoise belt. All positions are render-only.
     */
    private void drawTravellingNumber(Canvas c,int x,int y,Node n,boolean upper) {
        int value=upper?n.crossItem:n.item;
        if(value==EMPTY)return;
        float s=sz();
        Travel v=upper?n.upperTravel:n.lowerTravel;
        float cx=x+.5f,cy=y+.5f,wx=cx,wy=cy;
        int incoming=n.type==BELT?beltIncomingDir(x,y,n):n.dir;
        boolean targetTurn=n.type==BELT && isElbow(incoming,n.dir);
        if(targetTurn) {
            wx=cx+(DX[n.dir]-DX[incoming])*.125f;
            wy=cy+(DY[n.dir]-DY[incoming])*.125f;
        }
        float scale=targetTurn?.82f:1f;
        if(v.start>=0f) {
            float t=Math.max(0f,Math.min(1f,(clock-v.start)/v.duration));
            Node source=get((int)v.fromX,(int)v.fromY);
            float sx=v.fromX+.5f,sy=v.fromY+.5f;
            float dx=cx-sx,dy=cy-sy;
            if(t<1f && Math.abs(dx)+Math.abs(dy)>.001f) {
                // Mine outputs may be two tiles away from their centre; they
                // start at the perimeter instead of interpolating across the
                // 3x3 deposit and the empty ground outside its conveyors.
                if(source!=null && source.type==SOURCE) {
                    int travelDir=Math.abs(dx)>Math.abs(dy)?
                        (dx>0f?0:2):(dy>0f?1:3);
                    sx=cx-DX[travelDir]*.5f;
                    sy=cy-DY[travelDir]*.5f;
                    wx=lerp(sx,cx,t);
                    wy=lerp(sy,cy,t);
                } else if(Math.abs(dx)+Math.abs(dy)!=1f) {
                    // Guard against imported/older save layouts with
                    // non-neighbour transport: never extrapolate a bend.
                    wx=lerp(sx,cx,t);
                    wy=lerp(sy,cy,t);
                } else {
                    int d=dx>0f?0:dx<0f?2:dy>0f?1:3;
                    boolean sourceTurn=false;
                    int si=d;
                    if(source!=null && source.type==BELT) {
                        si=beltIncomingDir((int)v.fromX,(int)v.fromY,source);
                        sourceTurn=isElbow(si,source.dir)&&source.dir==d;
                    }
                    float sourceScale=sourceTurn?.82f:1f;
                    scale=lerp(sourceScale,scale,t);
                    if(t<.5f) {
                        float u=t*2f;
                        if(sourceTurn) {
                            float midX=sx+(DX[d]-DX[si])*.125f;
                            float midY=sy+(DY[d]-DY[si])*.125f;
                            wx=quad(midX,sx+DX[d]*.25f,sx+DX[d]*.5f,u);
                            wy=quad(midY,sy+DY[d]*.25f,sy+DY[d]*.5f,u);
                        } else {
                            wx=sx+DX[d]*(.5f*u);
                            wy=sy+DY[d]*(.5f*u);
                        }
                    } else {
                        float u=t*2f-1f;
                        if(targetTurn && incoming==d) {
                            float midX=cx+(DX[n.dir]-DX[d])*.125f;
                            float midY=cy+(DY[n.dir]-DY[d])*.125f;
                            wx=quad(cx-DX[d]*.5f,cx-DX[d]*.25f,midX,u);
                            wy=quad(cy-DY[d]*.5f,cy-DY[d]*.25f,midY,u);
                        } else {
                            wx=cx-DX[d]*(.5f*(1f-u));
                            wy=cy-DY[d]*(.5f*(1f-u));
                        }
                    }
                }
            }
        }
        // Independent bridge lanes retain their small lateral separation.
        if(n.type==BRIDGE) {
            int d=upper?n.crossDir:n.dir;
            if(d>=0&&d<4) {
                float side=upper?-.10f:.10f;
                wx+=-DY[d]*side;
                wy+=DX[d]*side;
            }
        }
        float px=panX+wx*s,py=panY+wy*s;
        if(px< -s||px>getWidth()+s||py< -s||py>getHeight()+s)return;
        float size=(upper||n.type==BRIDGE)?s*.68f:s*scale;
        token(c,px,py,size,value);
    }

    /**
     * One rounded exterior for rectangular and L-shaped machines.
     * 2x2, 3x1, 3x3 are round rectangles. Both L-shapes use a rounded
     * six-vertex contour, rotated with their logical machine footprints.
     */
    private Path machineSilhouette(int type,int rotation,float left,float top,float unit) {
        MachineLayout.Shape shape=MachineLayout.shape(type,rotation);
        Path result=new Path();
        if(shape==null)return result;
        float radius=unit*.28f; // Same soft corner radius as the miner.
        if(shape.cells.length==shape.width*shape.height) {
            result.addRoundRect(left,top,left+shape.width*unit,
                top+shape.height*unit,radius,radius,Path.Direction.CW);
            return result;
        }
        // Default L is the 2x2 contour: upper row full, lower right.
        float[][] base={{0,0},{2,0},{2,2},{1,2},{1,1},{0,1}};
        float[][] pts=new float[base.length][2];
        for(int i=0;i<base.length;i++) {
            float x=base[i][0],y=base[i][1];
            float nx,ny;
            switch(rotation&3) {
                case 1: nx=2-y;ny=x;break;
                case 2: nx=2-x;ny=2-y;break;
                case 3: nx=y;ny=2-x;break;
                default:nx=x;ny=y;
            }
            pts[i][0]=left+nx*unit;
            pts[i][1]=top+ny*unit;
        }
        for(int i=0;i<pts.length;i++) {
            float[] prev=pts[(i+pts.length-1)%pts.length];
            float[] cur=pts[i], next=pts[(i+1)%pts.length];
            float dx0=prev[0]-cur[0],dy0=prev[1]-cur[1];
            float dx1=next[0]-cur[0],dy1=next[1]-cur[1];
            float len0=(float)Math.hypot(dx0,dy0);
            float len1=(float)Math.hypot(dx1,dy1);
            float cut=Math.min(radius,Math.min(len0,len1)*.40f);
            float inX=cur[0]+dx0*cut/len0,inY=cur[1]+dy0*cut/len0;
            float outX=cur[0]+dx1*cut/len1,outY=cur[1]+dy1*cut/len1;
            if(i==0)result.moveTo(inX,inY);
            else result.lineTo(inX,inY);
            result.quadTo(cur[0],cur[1],outX,outY);
        }
        result.close();
        return result;
    }

    private void drawMultiMachine(Canvas c,Node controller) {
        drawMultiMachineAt(c,controller,controller.originX,controller.originY);
    }

    private void drawMultiMachineAt(Canvas c,Node controller,int gridX,int gridY) {
        MachineLayout.Shape shape=MachineLayout.shape(controller.type,controller.rotation);
        if(shape==null)return;
        float s=sz(),ox=panX+gridX*s,oy=panY+gridY*s;
        if(ox>getWidth()+s||oy>getHeight()+s||
           ox+shape.width*s< -s||oy+shape.height*s< -s)return;
        int outer=controller.type==ADD?0xff503080:controller.type==MULTIPLY?
                  0xff254a80:controller.type==STORAGE?0xff31576a:
                  controller.type==SUBTRACT?0xff6a405f:0xff344d70;
        int inner=controller.type==ADD?0xff7950ae:controller.type==MULTIPLY?
                  0xff3671ae:controller.type==STORAGE?0xff376777:
                  controller.type==SUBTRACT?0xff8d5278:0xff4b799d;
        Path silhouette=machineSilhouette(controller.type,controller.rotation,ox,oy,s);
        // Dark outer rim and single rounded continuous face. No grid seams.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(Math.max(2*density,s*.13f));
        p.setColor(0xff10202d);
        c.drawPath(silhouette,p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(outer);
        c.drawPath(silhouette,p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1*density,s*.045f));
        p.setColor(0xffa190ca);
        c.drawPath(silhouette,p);
        p.setStyle(Paint.Style.FILL);
        Path innerFace=new Path(silhouette);
        android.graphics.Matrix inset=new android.graphics.Matrix();
        inset.setScale(.93f,.93f,ox+shape.width*s*.5f,oy+shape.height*s*.5f);
        innerFace.transform(inset);
        p.setColor(inner);
        c.drawPath(innerFace,p);
        float fx=0f,fy=0f,portX=0f,portY=0f;
        int count=0,outputs=0;
        for(MachineLayout.Cell part:shape.cells) {
            fx+=part.x+.5f;fy+=part.y+.5f;count++;
            if(part.port==MachineLayout.OUTPUT) {
                portX+=part.x+.5f;portY+=part.y+.5f;outputs++;
            }
            if(part.port==MachineLayout.A||part.port==MachineLayout.B||
               part.port==MachineLayout.REMAINDER) {
                String name=part.port==MachineLayout.A?"A":
                    part.port==MachineLayout.B?"B":"R";
                float px=ox+(part.x+.5f)*s,py=oy+(part.y+.68f)*s;
                // Small port symbols sit on ONE shared machine surface.
                p.setColor(0xff352b4a);
                c.drawCircle(px,py-s*.13f,s*.24f,p);
                label(c,name,px,py,s*.32f,0xffffe5b8,true,Paint.Align.CENTER);
            }
        }
        fx/=Math.max(1,count);fy/=Math.max(1,count);
        String symbol=controller.type==ADD?"+":controller.type==MULTIPLY?"×":
                      controller.type==SUBTRACT?"−":controller.type==DIVIDE?"÷":
                      controller.type==EXPONENT?"^":"▣";
        if(controller.type==STORAGE) {
            label(c,"▣",ox+shape.width*s*.5f,oy+shape.height*s*.48f,
                  s*.83f,0xffedfff8,true,Paint.Align.CENTER);
            label(c,controller.storedCount+" / 10000",
                  ox+shape.width*s*.5f,oy+shape.height*s*.68f,
                  s*.30f,0xffdefff6,true,Paint.Align.CENTER);
        } else {
            // Large central operation symbol plus one shared result label.
            label(c,symbol,ox+fx*s,oy+fy*s+s*.23f,s*.70f,
                  0xfff4f2ff,true,Paint.Align.CENTER);
            if(outputs>0) {
                String result=controller.type==ADD?"A+B":
                              controller.type==MULTIPLY?"A×B":
                              controller.type==SUBTRACT?"A−B":
                              controller.type==DIVIDE?"A/B":"A^B";
                label(c,result,ox+(portX/outputs)*s,
                      oy+(portY/outputs)*s-s*.11f,s*.23f,
                      0xffe4d9fc,true,Paint.Align.CENTER);
            }
        }
    }

    private void drawNode(Canvas c,int x,int y,Node n) {
        if(n.type==PART)return;
        if(n.multi){drawMultiMachine(c,n);return;}
        float s=sz(),cx=panX+(x+.5f)*s,cy=panY+(y+.5f)*s;
        if(cx<-s || cy<-s || cx>getWidth()+s || cy>getHeight()+s)return;
        if(n.type==BELT) {
            int incoming=beltIncomingDir(x,y,n);
            if(isElbow(incoming,n.dir)) {
                // Same wide turquoise belt material as v0.1.0, now following a bend.
                Path corner=new Path();
                corner.moveTo(cx-DX[incoming]*s*.50f,cy-DY[incoming]*s*.50f);
                corner.quadTo(cx,cy,cx+DX[n.dir]*s*.50f,cy+DY[n.dir]*s*.50f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.BUTT);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setStrokeWidth(s*.49f);p.setColor(0xff122a3d);c.drawPath(corner,p);
                p.setStrokeWidth(s*.38f);p.setColor(0xff195263);c.drawPath(corner,p);
                // No continuous centre stripe on elbows: moving chevrons
                // alone indicate flow, matching the original straight belts.
                // Animate the same repeating chevrons as straight belts.
                // Follow the actual quadratic bend rather than a static arrow
                // oriented only in the outgoing direction.
                PathMeasure measure=new PathMeasure(corner,false);
                float length=measure.getLength();
                float spacing=s*.24f;
                float shift=((clock*1.5f)%1f)*spacing;
                float[] pos=new float[2],tangent=new float[2];
                p.setColor(0xff47d1c8);p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f,s*.028f));
                for(float dist=shift;dist<length;dist+=spacing) {
                    // Keep full arrows inside the current tile, just as
                    // the straight-belt renderer clips at its two ends.
                    if(dist<s*.09f||dist>length-s*.09f)continue;
                    if(!measure.getPosTan(dist,pos,tangent))continue;
                    float tailX=pos[0]-tangent[0]*s*.058f;
                    float tailY=pos[1]-tangent[1]*s*.058f;
                    float normalX=-tangent[1]*s*.074f;
                    float normalY=tangent[0]*s*.074f;
                    c.drawLine(tailX+normalX,tailY+normalY,pos[0],pos[1],p);
                    c.drawLine(tailX-normalX,tailY-normalY,pos[0],pos[1],p);
                }
                p.setStyle(Paint.Style.FILL);
            } else {
                c.save();
                c.rotate(n.dir*90f,cx,cy);
                box(c,cx-s*.49f,cy-s*.245f,cx+s*.49f,cy+s*.245f,
                    s*.11f,0xff122a3d);
                box(c,cx-s*.47f,cy-s*.19f,cx+s*.47f,cy+s*.19f,
                    s*.08f,0xff195263);
                p.setColor(0xff47d1c8);p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1,s*.028f));
                for(int a=-2;a<=2;a++) {
                    float shift=((clock*1.5f)%1f)*s*.24f;
                    float tx=cx+a*s*.24f+shift;
                    if(tx>cx+s*.40f||tx<cx-s*.40f)continue;
                    c.drawLine(tx-s*.058f,cy-s*.074f,tx,cy,p);
                    c.drawLine(tx,cy,tx-s*.058f,cy+s*.074f,p);
                }
                p.setStyle(Paint.Style.FILL);
                c.restore();
    
            }
        } else if(n.type==BRIDGE) {
            drawBridgeLane(c,cx,cy,s,n.dir,false);
            if(n.crossDir>=0)drawBridgeLane(c,cx,cy,s,n.crossDir,true);
        } else if(n.type==SOURCE) {
            // A built extractor occupies the visible 3x3 number field.
            box(c,cx-s*1.40f,cy-s*1.40f,cx+s*1.40f,cy+s*1.40f,
                s*.27f,0xff634c37);
            box(c,cx-s*1.22f,cy-s*1.22f,cx+s*1.22f,cy+s*1.22f,
                s*.21f,0xffdcaa63);
            box(c,cx-s*.88f,cy-s*.85f,cx+s*.88f,cy+s*.55f,
                s*.16f,0xfff7d18c);
            label(c,Integer.toString(n.value),cx,cy+s*.32f,
                  s*1.05f,0xff302331,true,Paint.Align.CENTER);
            label(c,"ДОБЫЧА "+upgradeLevels[1],cx,cy+s*1.02f,
                  s*.32f,0xff432b27,true,Paint.Align.CENTER);
        } else if(n.type==STORAGE) {
            box(c,cx-s*.48f,cy-s*.48f,cx+s*.48f,cy+s*.48f,s*.12f,0xff4d6374);
            box(c,cx-s*.39f,cy-s*.39f,cx+s*.39f,cy+s*.39f,s*.08f,0xff203c4b);
            label(c,"▣",cx,cy-s*.035f,s*.36f,0xffb4f4e5,true,Paint.Align.CENTER);
            label(c,n.storedCount+" / 10000",cx,cy+s*.25f,s*.14f,
                0xffdefff9,true,Paint.Align.CENTER);
            if(n.storedCount>0)label(c,Integer.toString(n.value),cx,cy+s*.43f,s*.12f,
                GOLD,true,Paint.Align.CENTER);
        } else if(n.type==ADD||n.type==MULTIPLY||n.type==SUBTRACT||
                  n.type==DIVIDE||n.type==EXPONENT) {
            int color=n.type==ADD?PURPLE:n.type==MULTIPLY?BLUE:0xffffaa8d;
            box(c,cx-s*.50f,cy-s*.50f,cx+s*.50f,cy+s*.50f,
                s*.14f,0x5533c8c2);
            box(c,cx-s*.44f,cy-s*.45f,cx+s*.44f,cy+s*.45f,
                s*.12f,n.type==ADD?0xff392956:n.type==MULTIPLY?0xff253c62:0xff4b3147);
            box(c,cx-s*.38f,cy-s*.39f,cx+s*.38f,cy+s*.39f,
                s*.09f,n.type==ADD?0xff7752b3:n.type==MULTIPLY?0xff426dba:0xff8b5671);
            String op=n.type==ADD?"+":n.type==MULTIPLY?"×":n.type==SUBTRACT?"−":
                n.type==DIVIDE?"÷":"^";
            label(c,op,cx,cy+s*.17f,
                  s*.54f,0xfff8f7ff,true,Paint.Align.CENTER);
            label(c,"A",cx-s*.31f,cy-s*.28f,s*.14f,
                  0xffe5d4ff,true,Paint.Align.CENTER);
            label(c,"B",cx-s*.31f,cy+s*.34f,s*.14f,
                  0xffe5d4ff,true,Paint.Align.CENTER);
            p.setColor(color);
            c.drawCircle(cx+s*.37f,cy,s*.055f,p);
            if(n.a!=EMPTY) {
                p.setColor(GOLD);c.drawCircle(cx-s*.14f,cy-s*.32f,s*.065f,p);
            }
            if(n.b!=EMPTY) {
                p.setColor(GOLD);c.drawCircle(cx-s*.14f,cy+s*.32f,s*.065f,p);
            }
        } else if(n.type==HUB) {
            for(int a=4;a>=1;a--) {
                p.setColor(Color.argb(15,56,210,204));
                c.drawCircle(cx,cy,s*(.49f+a*.14f+flash*.03f),p);
            }
            p.setColor(0xff235365);c.drawCircle(cx,cy,s*.52f,p);
            p.setColor(0xff40d1c9);c.drawCircle(cx,cy,s*.43f,p);
            p.setColor(0xff102b3b);c.drawCircle(cx,cy,s*.36f,p);
            label(c,"◎",cx,cy+s*.14f,s*.48f,
                  0xffa8ffed,true,Paint.Align.CENTER);
            label(c,"ХАБ",cx,cy+s*.66f,s*.15f,
                  0xff88eee4,true,Paint.Align.CENTER);
        }
    }

    private void drawHud(Canvas c) {
        float w=getWidth(),d=density;
        box(c,8*d,8*d,w-8*d,116*d,21*d,0xf20d2333);
        box(c,12*d,12*d,w-12*d,15*d,2*d,0xff277b83);
        label(c,"NUMERA",22*d,43*d,25*d,0xffdbfaf9,true,Paint.Align.LEFT);
        label(c,"Ф А Б Р И К А   Ч И С Е Л",23*d,58*d,9*d,
              0xff85aebe,false,Paint.Align.LEFT);
        label(c,"ПРОКАЧКА  ⇧",w-22*d,58*d,11*d,
              0xff97e8d7,true,Paint.Align.RIGHT);
        label(c,"УРОВЕНЬ "+level,w-22*d,38*d,12*d,
              0xff7bddce,true,Paint.Align.RIGHT);
        label(c,"ЦЕЛЬ: "+goal,22*d,85*d,17*d,
              GOLD,true,Paint.Align.LEFT);
        label(c,delivered+" / "+required,w-22*d,84*d,16*d,
              0xffe8f4f8,true,Paint.Align.RIGHT);
        box(c,22*d,95*d,w-22*d,103*d,4*d,0xff284657);
        float f=Math.min(1f,(float)delivered/Math.max(1,required));
        if(f>0)box(c,22*d,95*d,22*d+(w-44*d)*f,103*d,
                  4*d,0xff52ded0);
    }
    private void drawUpgradePanel(Canvas c) {
        float d=density,w=getWidth(),top=120*d;
        float bottom=Math.min(getHeight()-100*d,top+338*d);
        box(c,8*d,top,w-8*d,bottom,18*d,0xf5092639);
        label(c,"УЛУЧШЕНИЯ  ·  нажми для покупки",20*d,top+25*d,14*d,
              0xffd6fff5,true,Paint.Align.LEFT);
        int maxKinds=Math.min(upgradeLevels.length,(int)((bottom-top-32*d)/(50*d)));
        for(int i=0;i<maxKinds;i++) {
            float y=top+(32+50*i)*d;
            if(i%2==0)box(c,14*d,y,w-14*d,y+47*d,9*d,0xff183d4b);
            boolean available=upgradeUnlocked(i);
            int[][] req=requirements(i);
            String name=UPGRADE_NAMES[i]+"   ур. "+upgradeLevels[i];
            label(c,name,23*d,y+18*d,13*d,available?0xffe6fff8:0xff708c9b,true,Paint.Align.LEFT);
            if(!available) {
                label(c,"Откроется на уровне "+7,23*d,y+38*d,10*d,
                      0xffd0aebd,false,Paint.Align.LEFT);
                continue;
            }
            if(req.length==0) {
                label(c,"Следующие ступени в разработке",23*d,y+38*d,10*d,
                      0xffa0c4d0,false,Paint.Align.LEFT);
                continue;
            }
            StringBuilder parts=new StringBuilder();
            boolean all=true;
            for(int[] pair:req) {
                if(parts.length()>0)parts.append("  ·  ");
                long have=upgradeBanks.get(i).getOrDefault(pair[0],0L);
                parts.append(pair[0]).append(": ").append(Math.min(pair[1],have))
                     .append("/").append(pair[1]);
                if(have<pair[1])all=false;
            }
            label(c,parts.toString(),23*d,y+38*d,Math.max(8f,10f-req.length*.5f)*d,
                  all?0xff79f0c9:0xffaac9d1,false,Paint.Align.LEFT);
            label(c,all?"ГОТОВО":"",w-22*d,y+18*d,10*d,
                  0xff7df1c6,true,Paint.Align.RIGHT);
        }
    }
    private void tapUpgradePanel(float x,float y) {
        float top=120*density;
        if(y<top||y>top+338*density){upgradePanel=false;return;}
        int row=(int)((y-top-32*density)/(50*density));
        if(row>=0&&row<upgradeLevels.length&&y>=top+32*density)buyUpgrade(row);
    }

    private void drawToolbar(Canvas c) {
        float d=density,w=getWidth(),h=getHeight();
        float top=h-94*d;
        box(c,6*d,top,w-6*d,h+18*d,19*d,0xff0c2232);
        float padding=11*d;
        float itemW=(w-2*padding)/TOOLS.length;
        for(int i=0;i<TOOLS.length;i++) {
            float l=padding+i*itemW,mid=l+itemW*.5f;
            boolean on=selected==i;
            if(on) {
                box(c,l+2*d,top+7*d,l+itemW-2*d,h-8*d,
                    13*d,0xff20505c);
                box(c,l+9*d,top+7*d,l+itemW-9*d,top+10*d,
                    2*d,CYAN);
            }
            label(c,ICONS[i],mid,top+47*d,23*d,
                  on?0xffd3ffef:0xffa8c0cd,true,Paint.Align.CENTER);
            label(c,TOOLS[i],mid,top+69*d,8*d,
                  on?0xffd4fff9:0xff8ba6b3,false,Paint.Align.CENTER);
            if(i==3&&level<7)
                label(c,"ур. "+7,mid,top+83*d,8*d,
                    0xffffbd85,false,Paint.Align.CENTER);
        }
    }

    private int gridX(float screenX) {return (int)Math.floor(worldX(screenX));}
    private int gridY(float screenY) {return (int)Math.floor(worldY(screenY));}
    private boolean within(int x,int y) {
        return Math.abs(x)<=3000 && Math.abs(y)<=3000;
    }
    private void place(int x,int y) {
        if(!within(x,y)||isHub(x,y))return;
        Node old=get(x,y);
        if(selected==0) {
            if(depositUnder(x,y)>0)return;
            if(old!=null && old.type==BELT) old.dir=(old.dir+1)%4;
            else if(old==null)put(x,y,BELT,0,0);
        } else if(selected==1) {
            Point centre=depositCenterAt(x,y);
            if(centre==null||!within(centre.x,centre.y)||isHub(centre.x,centre.y))
                return;
            int value=depositValue(centre.x,centre.y);
            if(value<1||value>extractionMax())return; // Only unlocked numbers can be extracted.
            Node miner=get(centre.x,centre.y);
            if(miner==null)put(centre.x,centre.y,SOURCE,0,value);
            // A repeated touch anywhere in the same 3x3 field reuses
            // its existing extractor, never creating a second one.
        } else if(selected==2||selected==3) {
            if(operatorUnlocked(operatorChoice))
                putMachine(x,y,operatorChoice,machineRotation);
        } else if(selected==4) {
            eraseCell(x,y);
        } else if(selected==6) {
            pasteClipboard(x,y);
        }
        save();
    }
    private void pasteClipboard(int x,int y) {
        if(clipboard.isEmpty())return;
        // A machine is always copied from its controller, never as detached
        // parts; placement atomically reserves its full rotated footprint.
        ArrayList<Long> keys=new ArrayList<>(clipboard.keySet());
        Collections.sort(keys);
        for(long key:keys) {
            Node n=clipboard.get(key);
            if(n==null||n.type==PART)continue;
            int xx=x+(int)(key>>32), yy=y+(int)key;
            if(n.multi) {
                int ox=x+n.originX,oy=y+n.originY;
                if(putMachine(ox,oy,n.type,n.rotation)) {
                    MachineLayout.Shape sh=MachineLayout.shape(n.type,n.rotation);
                    MachineLayout.Cell first=sh.cells[0];
                    Node owner=machineAt(ox+first.x,oy+first.y);
                    if(owner!=null)owner.upgrade=n.upgrade;
                }
                continue;
            }
            if(!within(xx,yy)||isHub(xx,yy)||get(xx,yy)!=null||
               depositUnder(xx,yy)>0)continue;
            if(n.type==SOURCE && depositValue(xx,yy)!=n.value)continue;
            put(xx,yy,n.type,n.dir,n.value);
            get(xx,yy).upgrade=n.upgrade;
            get(xx,yy).crossDir=n.crossDir;
            // Never duplicate production inventory when pasting a building.
            get(xx,yy).storedCount=0;
        }
    }
    private void eraseCell(int x,int y) {
        if(!within(x,y)||isHub(x,y))return;
        removeBuilding(x,y);
    }
    private void erasePath(int x0,int y0,int x1,int y1) {
        if(!within(x0,y0)||!within(x1,y1))return;
        int x=x0,y=y0,iter=0;
        eraseCell(x,y);
        while((x!=x1||y!=y1)&&iter++<6000) {
            if(x!=x1)x+=Integer.signum(x1-x);
            else y+=Integer.signum(y1-y);
            eraseCell(x,y);
        }
    }
    private void eraseSelection() {
        if(!selectionReady)return;
        int lowX=Math.min(selectX0,selectX1),highX=Math.max(selectX0,selectX1);
        int lowY=Math.min(selectY0,selectY1),highY=Math.max(selectY0,selectY1);
        ArrayList<Long> erase=new ArrayList<>();
        for(Map.Entry<Long,Node> e:cells.entrySet()) {
            long key=e.getKey();int x=(int)(key>>32),y=(int)key;
            if(x>=lowX&&x<=highX&&y>=lowY&&y<=highY&&e.getValue().type!=HUB)
                erase.add(key);
        }
        for(long key:erase)eraseCell((int)(key>>32),(int)key);
        selectionReady=false;
        save();
    }
    private void drawSelectionMenu(Canvas c) {
        float d=density,w=getWidth();
        float top=getHeight()-(TOOLBAR_DP+BELT_MENU_DP)*d;
        box(c,6*d,top,w-6*d,top+BELT_MENU_DP*d,12*d,0xff173448);
        String[] options={"Копировать","Удалить","Отмена"};
        for(int i=0;i<3;i++) {
            float l=w*i/3f,right=w*(i+1)/3f;
            if(selectionReady)box(c,l+4*d,top+4*d,right-4*d,top+39*d,
                8*d,i==1?0xff603f50:0xff22525c);
            label(c,options[i],(l+right)*.5f,top+27*d,11*d,
                selectionReady?0xffe1fff9:0xff7999a4,true,Paint.Align.CENTER);
        }
    }
    private void copySelection() {
        clipboard.clear();
        int minX=Math.min(selectX0,selectX1),maxX=Math.max(selectX0,selectX1);
        int minY=Math.min(selectY0,selectY1),maxY=Math.max(selectY0,selectY1);
        if(maxX-minX>100||maxY-minY>100)return;
        for(Map.Entry<Long,Node> e:cells.entrySet()) {
            int x=(int)(e.getKey()>>32),y=(int)(long)e.getKey();
            if(x<minX||x>maxX||y<minY||y>maxY||e.getValue().type==HUB)continue;
            Node n=e.getValue();
            if(n.type==PART)continue;
            if(n.multi) {
                MachineLayout.Shape shape=MachineLayout.shape(n.type,n.rotation);
                boolean entirelySelected=true;
                for(MachineLayout.Cell cell:shape.cells) {
                    int xx=n.originX+cell.x,yy=n.originY+cell.y;
                    if(xx<minX||xx>maxX||yy<minY||yy>maxY) {
                        entirelySelected=false;break;
                    }
                }
                if(!entirelySelected)continue;
            }
            Node clone=new Node(n.type,n.dir,n.value);
            clone.upgrade=n.upgrade;
            clone.crossDir=n.crossDir;
            clone.multi=n.multi;clone.rotation=n.rotation;
            clone.originX=n.originX-minX;clone.originY=n.originY-minY;
            clone.storedCount=0;
            clipboard.put(id(x-minX,y-minY),clone);
        }
        clipboardCount=clipboard.size();
    }
    private void placeBeltSegment(int x,int y,int dir,boolean turnAnchor) {
        if(!within(x,y)||isHub(x,y)||depositUnder(x,y)>0)return;
        Node n=get(x,y);
        if(n==null) {
            put(x,y,BELT,dir,0);
        } else if(n.type==BELT) {
            if(n.dir==dir)return;
            // The start of a new route leg is an elbow, not an overpass.
            if(turnAnchor || (n.dir&1)==(dir&1)) {
                n.dir=dir;
            } else if(level>=5) { // Complete level 4 to unlock bridges.
                n.type=BRIDGE;
                n.crossDir=dir;
            } // Otherwise keep the existing belt intact.
        }
        // An existing bridge retains both routes when another line is drawn.
    }

    private void drawBeltPath(int x0,int y0,int x1,int y1) {
        if(!within(x0,y0)||!within(x1,y1))return;
        int x=x0,y=y0,lastDir=0,guard=0;
        boolean start=true;
        while((x!=x1||y!=y1)&&guard++<3000) {
            int dx=0,dy=0;
            if(x!=x1)dx=Integer.signum(x1-x);
            else dy=Integer.signum(y1-y);
            int dir=dx==1?0:dx==-1?2:dy==1?1:3;
            placeBeltSegment(x,y,dir,start);
            lastDir=dir;
            x+=dx;y+=dy;start=false;
        }
        if(guard<3001)placeBeltSegment(x1,y1,lastDir,start);
    }
    private void routeTo(Point end) {
        if(end==null)return;
        if(route.isEmpty())route.add(new Point(end.x,end.y));
        else {
            Point last=route.get(route.size()-1);
            if(last.x!=end.x||last.y!=end.y)route.add(new Point(end.x,end.y));
        }
    }
    private void confirmRoute() {
        if(route.isEmpty())return;
        if(routeLive!=null)routeTo(routeLive);
        for(int i=1;i<route.size();i++) {
            Point from=route.get(i-1),to=route.get(i);
            drawBeltPath(from.x,from.y,to.x,to.y);
        }
        if(route.size()==1) {
            Point one=route.get(0);
            drawBeltPath(one.x,one.y,one.x,one.y);
        }
        route.clear();routeLive=null;save();
    }
    private void drawRoutePreview(Canvas c) {
        if(route.isEmpty())return;
        ArrayList<Point> points=new ArrayList<>(route);
        if(routeLive!=null)points.add(routeLive);
        float s=sz();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2f,s*.35f));
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xbb9debd6);
        Path preview=new Path();
        Point first=points.get(0);
        preview.moveTo(panX+(first.x+.5f)*s,panY+(first.y+.5f)*s);
        for(int i=1;i<points.size();i++) {
            Point from=points.get(i-1),to=points.get(i);
            preview.lineTo(panX+(to.x+.5f)*s,panY+(from.y+.5f)*s);
            preview.lineTo(panX+(to.x+.5f)*s,panY+(to.y+.5f)*s);
        }
        c.drawPath(preview,p);p.setStyle(Paint.Style.FILL);
        // Show the persistent construction anchor of one continuous belt.
        Point tail=route.get(route.size()-1);
        p.setColor(0xfffce6a3);
        c.drawCircle(panX+(tail.x+.5f)*s,panY+(tail.y+.5f)*s,
                     Math.max(3*density,s*.16f),p);
    }
    private void drawSelection(Canvas c) {
        if(selected!=5||(!selectionReady&&!selectionGesture))return;
        float s=sz();
        float left=panX+Math.min(selectX0,selectX1)*s;
        float top=panY+Math.min(selectY0,selectY1)*s;
        float right=panX+(Math.max(selectX0,selectX1)+1)*s;
        float bottom=panY+(Math.max(selectY0,selectY1)+1)*s;
        p.setColor(0x3340dccc);p.setStyle(Paint.Style.FILL);
        c.drawRect(left,top,right,bottom,p);
        p.setColor(0xff77eee4);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2*density);c.drawRect(left,top,right,bottom,p);
        p.setStyle(Paint.Style.FILL);
    }
    private float routeActionsTop() {
        return getHeight()-(TOOLBAR_DP+2*BELT_MENU_DP)*density;
    }
    private boolean hasRouteActions() {
        return selected==0&&beltMode==1&&!route.isEmpty();
    }
    private void drawBeltMenu(Canvas c) {
        float w=getWidth(),d=density,top=getHeight()-(TOOLBAR_DP+BELT_MENU_DP)*d;
        box(c,6*d,top,w-6*d,top+BELT_MENU_DP*d,12*d,0xff173448);
        String[] modes={"Рисование","Протяжка"};
        for(int i=0;i<2;i++) {
            float l=w*i*.5f,right=w*(i+1)*.5f;
            if(i==beltMode)
                box(c,l+4*d,top+3*d,right-4*d,top+41*d,10*d,0xff297679);
            label(c,modes[i],(l+right)*.5f,top+28*d,13*d,
                0xffe3fff7,true,Paint.Align.CENTER);
        }
        if(hasRouteActions()) {
            float y=routeActionsTop();
            box(c,6*d,y,w-6*d,y+BELT_MENU_DP*d,12*d,0xff173448);
            String[] actions={"Подтвердить","Отменить"};
            for(int i=0;i<2;i++) {
                float l=w*i*.5f,right=w*(i+1)*.5f;
                box(c,l+4*d,y+3*d,right-4*d,y+41*d,10*d,
                    i==0?0xff21766a:0xff583a48);
                label(c,actions[i],(l+right)*.5f,y+28*d,13*d,
                    0xffeffff8,true,Paint.Align.CENTER);
            }
        }
    }
    private void updateEdgeScroll(float dt) {
        if(!(paintGesture||routeGesture||eraseGesture)||pinching||toolbarGesture)return;
        // Wider active margins make long-distance belt placement manageable.
        // Scroll starts gradually before the finger hits the physical edge.
        float bandX=Math.min(94*density,getWidth()*.24f);
        float bandY=Math.min(104*density,getHeight()*.18f);
        float top=124*density,bottom=getHeight()-
                      (selected==0?(TOOLBAR_DP+BELT_MENU_DP*
                          (hasRouteActions()?2f:1f)):TOOLBAR_DP)*density;
        float speed=sz()*11f*dt;
        float sx=gestureX,sy=gestureY;
        float left=Math.max(0f,Math.min(1f,(bandX-sx)/bandX));
        float right=Math.max(0f,Math.min(1f,(sx-(getWidth()-bandX))/bandX));
        float up=Math.max(0f,Math.min(1f,(top+bandY-sy)/bandY));
        float down=Math.max(0f,Math.min(1f,(sy-(bottom-bandY))/bandY));
        // Quadratic ramp: little movement upon entry, full speed at the edge.
        panX+=speed*(left*left-right*right);
        panY+=speed*(up*up-down*down);
        int gx=gridX(gestureX),gy=gridY(gestureY);
        if(selected==0 && beltMode==0 && paintGesture &&
                (gx!=priorGridX||gy!=priorGridY)) {
            drawBeltPath(priorGridX,priorGridY,gx,gy);
            priorGridX=gx;priorGridY=gy;
        } else if(selected==0 && beltMode==1 && routeGesture) {
            routeLive=new Point(gx,gy);
        } else if(selected==4&&eraseGesture&&(gx!=priorGridX||gy!=priorGridY)) {
            erasePath(priorGridX,priorGridY,gx,gy);
            priorGridX=gx;priorGridY=gy;
        }
    }
    private static float span(MotionEvent e) {
        return (float)Math.hypot(e.getX(0)-e.getX(1),
                                 e.getY(0)-e.getY(1));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int action=e.getActionMasked();
        float toolbarTop=getHeight()-TOOLBAR_DP*density;
        float menuTop=toolbarTop-BELT_MENU_DP*density;
        float actionsTop=routeActionsTop();
        float boardBottom=selected==0?
                (hasRouteActions()?actionsTop:menuTop):
                (selected==5||machineMenuVisible()?menuTop:toolbarTop);
        if(focusedMachine!=null)boardBottom=Math.min(boardBottom,machineEditMenuTop());
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                removeCallbacks(machineLongPress);
                machinePressActive=false;
                pressedMachine=null;
                editMenuGesture=false;editDragGesture=false;
                longPressTriggered=false;
                downX=lastX=gestureX=e.getX();
                downY=lastY=gestureY=e.getY();
                priorGridX=gridX(gestureX);priorGridY=gridY(gestureY);
                toolbarGesture=gestureY>=toolbarTop;
                beltMenuGesture=selected==0&&gestureY>=menuTop&&!toolbarGesture;
                routeMenuGesture=hasRouteActions()&&gestureY>=actionsTop
                    &&gestureY<menuTop&&!toolbarGesture;
                selectionMenuGesture=selected==5&&gestureY>=menuTop&&!toolbarGesture;
                machineMenuGesture=machineMenuVisible()&&gestureY>=menuTop&&!toolbarGesture;
                headerGesture=gestureY<116*density;
                upgradeGesture=upgradePanel&&gestureY>=120*density
                    &&gestureY<toolbarTop;
                dragged=false;pinching=false;
                routeGesture=false;paintGesture=false;eraseGesture=false;
                selectionGesture=false;
                if(focusedMachine!=null && !toolbarGesture &&
                   gestureY>=machineEditMenuTop() &&
                   gestureY<machineEditMenuTop()+BELT_MENU_DP*density) {
                    editMenuGesture=true;
                    return true;
                }
                if(focusedMachine!=null && editMoveMode &&
                   gestureY>=124*density &&
                   gestureY<machineEditMenuTop()) {
                    editDragGesture=true;
                    Node hit=machineAt(priorGridX,priorGridY);
                    editFingerDX=hit==focusedMachine?
                        priorGridX-focusedMachine.originX:0;
                    editFingerDY=hit==focusedMachine?
                        priorGridY-focusedMachine.originY:0;
                    editTargetX=priorGridX-editFingerDX;
                    editTargetY=priorGridY-editFingerDY;
                    return true;
                }
                Node touched=gestureY>=124*density&&gestureY<boardBottom?
                    machineAt(priorGridX,priorGridY):null;
                if(focusedMachine!=null && touched!=focusedMachine) {
                    focusedMachine=null;
                    editMoveMode=false;
                }
                if(touched!=null && !toolbarGesture && !headerGesture &&
                   !upgradeGesture && !beltMenuGesture && !routeMenuGesture &&
                   !selectionMenuGesture && !machineMenuGesture) {
                    pressedMachine=touched;
                    machinePressActive=true;
                    postDelayed(machineLongPress,450L);
                    return true;
                }
                if(!toolbarGesture&&!beltMenuGesture&&!routeMenuGesture
                    &&!selectionMenuGesture&&!machineMenuGesture&&!headerGesture&&!upgradeGesture
                    &&gestureY>=124*density&&gestureY<boardBottom) {
                    if(selected==0) {
                        if(beltMode==0) {
                            paintGesture=true;
                            Node existing=get(priorGridX,priorGridY);
                            paintStartedOnBelt=existing!=null && (existing.type==BELT || existing.type==BRIDGE);
                            if(!paintStartedOnBelt)place(priorGridX,priorGridY);
                        } else {
                            routeGesture=true;
                            if(route.isEmpty())
                                route.add(new Point(priorGridX,priorGridY));
                            // The next gesture extends the previous tail.
                            routeLive=new Point(priorGridX,priorGridY);
                        }
                    } else if(selected==4) {
                        eraseGesture=true;
                        eraseCell(priorGridX,priorGridY);
                    } else if(selected==5) {
                        selectionGesture=true;selectionReady=false;
                        selectX0=selectX1=priorGridX;
                        selectY0=selectY1=priorGridY;
                    }
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                removeCallbacks(machineLongPress);
                machinePressActive=false;
                pressedMachine=null;
                longPressTriggered=false;
                editDragGesture=false;
                if(e.getPointerCount()>=2) {
                    pinching=true;dragged=true;lastSpan=span(e);
                    paintGesture=false;routeGesture=false;eraseGesture=false;
                    selectionGesture=false;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if(editDragGesture&&!pinching) {
                    editTargetX=gridX(e.getX())-editFingerDX;
                    editTargetY=gridY(e.getY())-editFingerDY;
                    return true;
                }
                if(longPressTriggered)return true;
                if(machinePressActive) {
                    float mx=e.getX(),my=e.getY();
                    if(Math.hypot(mx-downX,my-downY)>12*density) {
                        removeCallbacks(machineLongPress);
                        machinePressActive=false;
                        // Normal camera drag remains available if a finger
                        // starts on top of an existing factory building.
                        if(selected==7) {
                            panX+=mx-lastX;panY+=my-lastY;
                        }
                        dragged=true;
                    }
                    lastX=mx;lastY=my;
                    return true;
                }
                if(editMenuGesture)return true;
                if(e.getPointerCount()>=2) {
                    float current=span(e);
                    if(lastSpan>5f&&current>5f) {
                        float midX=(e.getX(0)+e.getX(1))*.5f;
                        float midY=(e.getY(0)+e.getY(1))*.5f;
                        float wx=worldX(midX),wy=worldY(midY);
                        zoom=Math.max(.12f,Math.min(3.5f,zoom*current/lastSpan));
                        panX=midX-wx*sz();panY=midY-wy*sz();
                    }
                    lastSpan=current;return true;
                }
                if(toolbarGesture||beltMenuGesture||routeMenuGesture
                    ||selectionMenuGesture||machineMenuGesture||pinching||upgradeGesture
                    ||headerGesture)return true;
                gestureX=e.getX();gestureY=e.getY();
                if(selected==7) {
                    panX+=gestureX-lastX;panY+=gestureY-lastY;dragged=true;
                } else if(selected==0) {
                    int gx=gridX(gestureX),gy=gridY(gestureY);
                    if(beltMode==0&&paintGesture
                       &&(gx!=priorGridX||gy!=priorGridY)) {
                        drawBeltPath(priorGridX,priorGridY,gx,gy);
                        priorGridX=gx;priorGridY=gy;dragged=true;
                    } else if(beltMode==1&&routeGesture) {
                        routeLive=new Point(gx,gy);dragged=true;
                    }
                } else if(selected==4&&eraseGesture) {
                    int gx=gridX(gestureX),gy=gridY(gestureY);
                    if(gx!=priorGridX||gy!=priorGridY) {
                        erasePath(priorGridX,priorGridY,gx,gy);
                        priorGridX=gx;priorGridY=gy;
                    }
                    dragged=true;
                } else if(selected==5&&selectionGesture) {
                    selectX1=gridX(gestureX);selectY1=gridY(gestureY);
                    dragged=true;
                } else if(Math.hypot(gestureX-downX,gestureY-downY)>8*density) {
                    dragged=true;panX+=gestureX-lastX;panY+=gestureY-lastY;
                }
                lastX=gestureX;lastY=gestureY;return true;
            case MotionEvent.ACTION_POINTER_UP:
                pinching=true;return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(machineLongPress);
                if(editMenuGesture&&!pinching&&focusedMachine!=null) {
                    if(e.getX()<getWidth()*.5f) {
                        editMoveMode=true;
                        editTargetX=focusedMachine.originX;
                        editTargetY=focusedMachine.originY;
                        setMachineMessage("Перетащи объект или нажми на новое место");
                    } else {
                        // Rotate at its saved anchor, never through belts.
                        if(relocateMachine(focusedMachine,focusedMachine.originX,
                           focusedMachine.originY,(focusedMachine.rotation+1)&3)) {
                            editMoveMode=false;
                            setMachineMessage("Поворот на 90°");
                        } else setMachineMessage("Нет места для поворота");
                    }
                    editMenuGesture=false;
                    return true;
                }
                if(editDragGesture&&!pinching&&focusedMachine!=null) {
                    editTargetX=gridX(e.getX())-editFingerDX;
                    editTargetY=gridY(e.getY())-editFingerDY;
                    if(relocateMachine(focusedMachine,editTargetX,editTargetY,
                                       focusedMachine.rotation)) {
                        focusedMachine=null;
                        editMoveMode=false;
                    } else setMachineMessage("Место занято — выбери другое");
                    editDragGesture=false;
                    return true;
                }
                if(longPressTriggered) {
                    longPressTriggered=false;
                    machinePressActive=false;pressedMachine=null;
                    return true;
                }
                if(machinePressActive) {
                    machinePressActive=false;pressedMachine=null;
                    // A short tap in the delete tool retains its normal job.
                    if(!pinching&&!dragged&&selected==4) {
                        eraseCell(gridX(e.getX()),gridY(e.getY()));
                        save();
                    }
                    return true;
                }
                if(upgradeGesture&&!pinching) {
                    tapUpgradePanel(e.getX(),e.getY());
                } else if(headerGesture&&!pinching) {
                    upgradePanel=!upgradePanel;
                } else if(toolbarGesture&&e.getY()>=toolbarTop) {
                    int index=(int)(e.getX()/Math.max(1f,getWidth()/8f));
                    selected=Math.max(0,Math.min(7,index));
                    focusedMachine=null;editMoveMode=false;
                    if(selected==2)operatorChoice=ADD;
                    else if(selected==3)operatorChoice=MULTIPLY;
                    if(selected!=5)selectionReady=false;
                } else if(routeMenuGesture&&selected==0&&!pinching) {
                    if(e.getX()<getWidth()*.5f)confirmRoute();
                    else {route.clear();routeLive=null;}
                } else if(beltMenuGesture&&selected==0&&!pinching) {
                    int mode=e.getX()<getWidth()*.5f?0:1;
                    if(mode!=beltMode) {
                        if(mode==0){route.clear();routeLive=null;}
                        beltMode=mode;
                    }
                } else if(machineMenuGesture&&machineMenuVisible()&&!pinching) {
                    int i=(int)(e.getX()/Math.max(1f,getWidth()/(float)(OPERATORS.length+1)));
                    if(i>=OPERATORS.length)machineRotation=(machineRotation+1)&3;
                    else if(i>=0 && operatorUnlocked(OPERATORS[i]))
                        operatorChoice=OPERATORS[i];
                } else if(selectionMenuGesture&&selected==5&&!pinching) {
                    int index=(int)(e.getX()/Math.max(1f,getWidth()/3f));
                    if(index==0&&selectionReady)copySelection();
                    else if(index==1&&selectionReady)eraseSelection();
                    else if(index==2)selectionReady=false;
                } else if(!pinching&&routeGesture&&beltMode==1) {
                    routeTo(new Point(gridX(e.getX()),gridY(e.getY())));
                    routeLive=null; // Remains preview until Confirm.
                } else if(!pinching&&paintGesture) {
                    if(!dragged && paintStartedOnBelt)
                        place(gridX(e.getX()),gridY(e.getY()));
                    else save();
                } else if(!pinching&&eraseGesture) {
                    erasePath(priorGridX,priorGridY,gridX(e.getX()),gridY(e.getY()));
                    save();
                } else if(!pinching&&selectionGesture) {
                    selectX1=gridX(e.getX());selectY1=gridY(e.getY());
                    selectionReady=true;
                } else if(!pinching&&!dragged&&selected!=7
                    &&e.getY()>=124*density&&e.getY()<boardBottom) {
                    place(gridX(e.getX()),gridY(e.getY()));
                }
                toolbarGesture=false;beltMenuGesture=false;routeMenuGesture=false;
                selectionMenuGesture=false;machineMenuGesture=false;headerGesture=false;upgradeGesture=false;
                paintGesture=false;routeGesture=false;eraseGesture=false;
                selectionGesture=false;pinching=false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(machineLongPress);
                machinePressActive=false;pressedMachine=null;
                editMenuGesture=false;editDragGesture=false;
                longPressTriggered=false;
                toolbarGesture=false;beltMenuGesture=false;routeMenuGesture=false;
                selectionMenuGesture=false;machineMenuGesture=false;headerGesture=false;upgradeGesture=false;
                paintGesture=false;routeGesture=false;eraseGesture=false;
                selectionGesture=false;pinching=false;routeLive=null;
                return true;
            default:return true;
        }
    }
}
