package com.numera.factory;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
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
    private static final int BELT=1, SOURCE=2, ADD=3, MULTIPLY=4, HUB=5;
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
    private int beltMode=0, selectX0,selectY0,selectX1,selectY1,clipboardCount;
    private boolean initialized,pinching,dragged,toolbarGesture,beltMenuGesture,routeGesture,paintGesture;
    private boolean eraseGesture,selectionGesture,selectionReady,selectionMenuGesture,routeMenuGesture;
    private float lastX,lastY,lastSpan,downX,downY,gestureX,gestureY;
    private int priorGridX,priorGridY;
    private final ArrayList<Point> route=new ArrayList<>();
    private Point routeLive;
    private final Map<Long,Node> clipboard=new HashMap<>();
    private static final String[] UPGRADE_NAMES={"Ленты","Добытчики","Сумматоры","Умножители"};
    // Amounts for the first eight tiers follow published Beltmatic upgrade tables.
    // The original varies some requested number IDs between worlds.
    private static final int[][][][] UPGRADE_REQ={{{{1,200}},{{1,1000},{16,500}},{{1,4000},{16,3000},{22,1500}},{{34,9000},{121,7000},{839,6000}},{{94,9000},{247,7000},{562,6000}},{{291,11000},{791,9000},{2319,8000}},{{854,15000},{1526,12000},{7891,10000}},{{1526,20000},{7891,16000},{4294,12000}}},{{{2,300}},{{2,2000},{18,1000}},{{2,5000},{18,2000},{23,1000}},{{16,8000},{22,3000},{438,2000}},{{89,10000},{378,4000},{787,3000}},{{291,12000},{791,8000},{2319,4000}},{{791,15000},{2319,12000},{7774,6000}},{{2319,20000},{7774,17000},{929,20000}}},{{{3,450}},{{3,3000},{11,1500}},{{3,5000},{11,4000},{75,3000}},{{11,7000},{91,5000},{193,4000}},{{91,12000},{193,10000},{625,5000}},{{193,15000},{625,12000},{3447,8000}},{{625,20000},{3447,17000},{9423,10000}},{{3447,30000},{9423,20000},{44031,16000}}},{{{15,500}},{{15,5000},{20,2500}},{{15,10000},{20,7000},{257,4000}},{{20,11000},{257,6000},{690,5000}},{{257,17000},{690,13000},{3644,6000}},{{690,22000},{3644,17000},{6433,10000}},{{3644,35000},{6433,25000},{49514,17000}},{{6433,37000},{49514,30000},{97001,20000}}}};
    private static final int[] GOAL_SEQUENCE={1,2,3,5,10,19,27,36,47,50,82,174,248,324,466,594,631,727,891,928};
    private static final int[] GOAL_COUNTS={10,20,35,40,50,100,150,300,450,500,600,800,1000,2000,3000,4000,5000,6000,7000,8000};
    private final int[] upgradeLevels={1,1,1,1};
    private final ArrayList<Map<Integer,Long>> upgradeBanks=new ArrayList<>();
    private boolean upgradePanel,upgradeGesture,headerGesture;


    private static final class Node {
        int type,dir,value,item=EMPTY,a=EMPTY,b=EMPTY,upgrade=1,extracted;
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
        for(int i=0;i<4;i++)upgradeBanks.add(new HashMap<>());
        load();
    }

    private static long id(int x,int y) {
        return (((long)x)<<32) | (y&0xffffffffL);
    }
    private void put(int x,int y,int type,int dir,int value) {
        cells.put(id(x,y),new Node(type,dir,value));
    }
    private Node get(int x,int y) { return cells.get(id(x,y)); }
    private void seed() {
        cells.clear();
        level=1;goal=1;required=10;delivered=0;allDelivered=0;
        for(int i=0;i<4;i++){upgradeLevels[i]=1;upgradeBanks.get(i).clear();}
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
            for(int i=0;i<4;i++)upgradeLevels[i]=Math.max(1,Integer.parseInt(levels[i]));
            String[] banks=parts[1].split(";",-1);
            for(int i=0;i<Math.min(4,banks.length);i++) {
                upgradeBanks.get(i).clear();
                for(String entry:banks[i].split(",")) {
                    if(entry.isEmpty())continue;
                    String[] pair=entry.split(":");
                    upgradeBanks.get(i).put(Integer.parseInt(pair[0]),Long.parseLong(pair[1]));
                }
            }
        }catch(Exception ex){
            for(int i=0;i<4;i++){upgradeLevels[i]=1;upgradeBanks.get(i).clear();}
        }
    }
    private String saveUpgrades() {
        StringBuilder b=new StringBuilder();
        for(int i=0;i<4;i++) {
            if(i>0)b.append(',');
            b.append(upgradeLevels[i]);
        }
        b.append('#');
        for(int i=0;i<4;i++) {
            if(i>0)b.append(';');
            for(Map.Entry<Integer,Long> e:upgradeBanks.get(i).entrySet())
                b.append(e.getKey()).append(':').append(e.getValue()).append(',');
        }
        return b.toString();
    }
    private void recordNumber(int number) {
        for(Map<Integer,Long> bank:upgradeBanks)bank.put(number,bank.getOrDefault(number,0L)+1);
    }
    private boolean upgradeUnlocked(int kind) {
        return kind<2||(kind==2&&level>=2)||(kind==3&&level>=7);
    }
    private int[][] requirements(int kind) {
        int step=upgradeLevels[kind]-1;
        return step<UPGRADE_REQ[kind].length?UPGRADE_REQ[kind][step]:new int[0][];
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
        int i=Math.max(0,Math.min(7,upgradeLevels[type==ADD?2:3]-1));
        float[] adder={.25f,1f/3f,.4f,.5f,2f/3f,1f,1.5f,2f};
        float[] multiplier={.2f,.3f,.4f,.5f,.6f,.7f,.8f,1f};
        return type==ADD?adder[i]:multiplier[i];
    }
    private int extractionMax() {
        if(level<=3)return 1;
        if(level<=5)return 2;
        if(level==6)return 3;
        if(level<=8)return 4;
        if(level<=10)return 5;
        return Math.min(999,Math.max(5,level-6));
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
                .append(n.value).append(',').append(n.upgrade).append(';');
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
                if(n.type==ADD||n.type==MULTIPLY) {
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
            if(entry.getValue().type!=HUB)
                drawNode(c,(int)(key>>32),(int)key,entry.getValue());
        }
        drawRoutePreview(c);
        drawSelection(c);
        c.restore();
        drawHud(c);
        drawToolbar(c);
        if(selected==0)drawBeltMenu(c);
        if(selected==5)drawSelectionMenu(c);
        if(upgradePanel)drawUpgradePanel(c);
        postInvalidateOnAnimation();
    }

    private void step() {
        ArrayList<Long> keys=new ArrayList<>(cells.keySet());
        HashSet<Long> entered=new HashSet<>();
        for(long key:keys) {
            Node n=cells.get(key);
            if(n==null || n.item==EMPTY || n.type==HUB)continue;
            int x=(int)(key>>32),y=(int)key;
            int d=n.dir;
            if(n.type==SOURCE)continue; // Miners output from the edge of the 3x3 field.
            int nx=x+DX[d],ny=y+DY[d];
            long next=id(nx,ny);
            Node dest=cells.get(next);
            if(dest==null)continue;
            if(dest.type==HUB) {
                recordNumber(n.item);
                allDelivered++;
                if(n.item==goal) {
                    delivered++;flash=1f;
                    if(delivered>=required) {
                        level++;
                        goal=GOAL_SEQUENCE[(level-1)%GOAL_SEQUENCE.length];
                        required=GOAL_COUNTS[(level-1)%GOAL_COUNTS.length];
                        delivered=0;
                    }
                }
                n.item=EMPTY;
                if((allDelivered&31)==0)save();
            } else if(dest.type==BELT && dest.item==EMPTY && !entered.contains(next)) {
                dest.item=n.item;
                n.item=EMPTY;
                entered.add(next);
            } else if(dest.type==ADD || dest.type==MULTIPLY) {
                if(y<ny && dest.a==EMPTY) {dest.a=n.item;n.item=EMPTY;}
                else if(y>ny && dest.b==EMPTY) {dest.b=n.item;n.item=EMPTY;}
                else if(dest.a==EMPTY) {dest.a=n.item;n.item=EMPTY;}
                else if(dest.b==EMPTY) {dest.b=n.item;n.item=EMPTY;}
            }
        }
        for(Map.Entry<Long,Node> entry:cells.entrySet()) {
            Node n=entry.getValue();
            if(n.type!=SOURCE)continue;
            int x=(int)(entry.getKey()>>32),y=(int)(long)entry.getKey();
            // 3x3 field, 12 perimeter output slots. Global upgrade controls
            // total throughput; one stream's worth at the initial level.
            int maxOutputs=Math.min(12,upgradeLevels[1]);
            int produced=0;
            for(int scan=0;scan<MINE_OUTPUTS.length && produced<maxOutputs;scan++) {
                int i=Math.floorMod(n.extracted+scan,MINE_OUTPUTS.length);
                int xx=x+MINE_OUTPUTS[i][0],yy=y+MINE_OUTPUTS[i][1];
                Node out=get(xx,yy);
                if(out!=null&&out.type==BELT&&out.item==EMPTY) {
                    out.item=n.value;n.extracted++;produced++;
                }
            }
        }
        for(Node n:cells.values()) {
            if((n.type==ADD || n.type==MULTIPLY) &&
               n.a!=EMPTY && n.b!=EMPTY && n.item==EMPTY && n.progress>=1f) {
                n.progress=0f;
                long result=n.type==ADD ? (long)n.a+n.b : (long)n.a*n.b;
                n.item=(int)Math.min(999999999L,Math.max(-999999999L,result));
                n.a=EMPTY;n.b=EMPTY;
            }
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

    private void drawNode(Canvas c,int x,int y,Node n) {
        float s=sz(),cx=panX+(x+.5f)*s,cy=panY+(y+.5f)*s;
        if(cx<-s || cy<-s || cx>getWidth()+s || cy>getHeight()+s)return;
        if(n.type==BELT) {
            int incoming=n.dir;
            for(int d=0;d<4;d++) {
                Node prev=get(x-DX[d],y-DY[d]);
                if(prev!=null && prev.dir==d && prev.type==BELT) {
                    incoming=d;
                    break;
                }
            }
            if(incoming!=n.dir && incoming!=(n.dir+2)%4) {
                // Same wide turquoise belt material as v0.1.0, now following a bend.
                Path corner=new Path();
                corner.moveTo(cx-DX[incoming]*s*.50f,cy-DY[incoming]*s*.50f);
                corner.quadTo(cx,cy,cx+DX[n.dir]*s*.50f,cy+DY[n.dir]*s*.50f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.BUTT);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setStrokeWidth(s*.49f);p.setColor(0xff122a3d);c.drawPath(corner,p);
                p.setStrokeWidth(s*.38f);p.setColor(0xff195263);c.drawPath(corner,p);
                p.setStrokeWidth(Math.max(1f,s*.025f));p.setColor(0xff47d1c8);
                c.drawPath(corner,p);
                p.setStyle(Paint.Style.FILL);
                c.save();
                c.rotate(n.dir*90f,cx,cy);
                float tx=cx+s*.22f;
                p.setColor(0xff47d1c8);p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f,s*.028f));
                c.drawLine(tx-s*.058f,cy-s*.074f,tx,cy,p);
                c.drawLine(tx,cy,tx-s*.058f,cy+s*.074f,p);
                p.setStyle(Paint.Style.FILL);
                c.restore();
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
        } else if(n.type==ADD||n.type==MULTIPLY) {
            int color=n.type==ADD?PURPLE:BLUE;
            box(c,cx-s*.50f,cy-s*.50f,cx+s*.50f,cy+s*.50f,
                s*.14f,0x5533c8c2);
            box(c,cx-s*.44f,cy-s*.45f,cx+s*.44f,cy+s*.45f,
                s*.12f,n.type==ADD?0xff392956:0xff253c62);
            box(c,cx-s*.38f,cy-s*.39f,cx+s*.38f,cy+s*.39f,
                s*.09f,n.type==ADD?0xff7752b3:0xff426dba);
            label(c,n.type==ADD?"+":"×",cx,cy+s*.17f,
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
        if(n.item!=EMPTY) token(c,cx,cy,s,n.item);
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
        float bottom=Math.min(getHeight()-100*d,top+322*d);
        box(c,8*d,top,w-8*d,bottom,18*d,0xf5092639);
        label(c,"УЛУЧШЕНИЯ  ·  нажми для покупки",20*d,top+25*d,14*d,
              0xffd6fff5,true,Paint.Align.LEFT);
        int maxKinds=Math.min(4,(int)((bottom-top-32*d)/(70*d)));
        for(int i=0;i<maxKinds;i++) {
            float y=top+(32+70*i)*d;
            if(i%2==0)box(c,14*d,y,w-14*d,y+66*d,9*d,0xff183d4b);
            boolean available=upgradeUnlocked(i);
            int[][] req=requirements(i);
            String name=UPGRADE_NAMES[i]+"   ур. "+upgradeLevels[i];
            label(c,name,23*d,y+23*d,15*d,available?0xffe6fff8:0xff708c9b,true,Paint.Align.LEFT);
            if(!available) {
                label(c,"Откроется на уровне "+(i==2?2:7),23*d,y+48*d,10*d,
                      0xffd0aebd,false,Paint.Align.LEFT);
                continue;
            }
            if(req.length==0) {
                label(c,"Следующие ступени в разработке",23*d,y+48*d,10*d,
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
            label(c,parts.toString(),23*d,y+48*d,Math.max(8f,10f-req.length*.5f)*d,
                  all?0xff79f0c9:0xffaac9d1,false,Paint.Align.LEFT);
            label(c,all?"ГОТОВО":"",w-22*d,y+23*d,10*d,
                  0xff7df1c6,true,Paint.Align.RIGHT);
        }
    }
    private void tapUpgradePanel(float x,float y) {
        float top=120*density;
        if(y<top||y>top+322*density){upgradePanel=false;return;}
        int row=(int)((y-top-32*density)/(70*density));
        if(row>=0&&row<4&&y>=top+32*density)buyUpgrade(row);
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
            if((i==2&&level<2)||(i==3&&level<7))
                label(c,"ур. "+(i==2?2:7),mid,top+83*d,8*d,
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
            int value=depositValue(x,y);
            if(value<1||value>extractionMax())return; // Only unlocked numbers can be extracted.
            if(old!=null&&old.type==SOURCE) {
                // Extractor upgrades are global, not purchased per deposit.
            } else if(old==null)put(x,y,SOURCE,0,value);
        } else if(selected==2) {
            if(level>=2 && old==null && depositUnder(x,y)<0)put(x,y,ADD,0,0);
        } else if(selected==3) {
            if(level>=7 && old==null && depositUnder(x,y)<0)put(x,y,MULTIPLY,0,0);
        } else if(selected==4) {
            eraseCell(x,y);
        } else if(selected==6) {
            pasteClipboard(x,y);
        }
        save();
    }
    private void pasteClipboard(int x,int y) {
        if(clipboard.isEmpty())return;
        for(Map.Entry<Long,Node> e:clipboard.entrySet()) {
            int xx=x+(int)(e.getKey()>>32), yy=y+(int)(long)e.getKey();
            Node n=e.getValue();
            if(!within(xx,yy)||isHub(xx,yy)||get(xx,yy)!=null||depositUnder(xx,yy)>0)continue;
            if(n.type==SOURCE && depositValue(xx,yy)!=n.value)continue;
            put(xx,yy,n.type,n.dir,n.value);
            get(xx,yy).upgrade=n.upgrade;
        }
    }
    private void eraseCell(int x,int y) {
        if(!within(x,y)||isHub(x,y))return;
        cells.remove(id(x,y));
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
        for(long key:erase)cells.remove(key);
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
            Node clone=new Node(n.type,n.dir,n.value);
            clone.upgrade=n.upgrade;
            clipboard.put(id(x-minX,y-minY),clone);
        }
        clipboardCount=clipboard.size();
    }
    private void drawBeltPath(int x0,int y0,int x1,int y1) {
        if(!within(x0,y0)||!within(x1,y1))return;
        int x=x0,y=y0, lastDir=0,guard=0;
        while((x!=x1||y!=y1)&&guard++<3000) {
            int dx=0,dy=0;
            if(x!=x1)dx=Integer.signum(x1-x);
            else dy=Integer.signum(y1-y);
            int dir=dx==1?0:dx==-1?2:dy==1?1:3;
            if(!isHub(x,y)&&depositUnder(x,y)<0) {
                Node n=get(x,y);
                if(n==null||n.type==BELT)put(x,y,BELT,dir,0);
            }
            lastDir=dir;
            x+=dx;y+=dy;
        }
        if(!isHub(x1,y1)&&depositUnder(x1,y1)<0) {
            Node n=get(x1,y1);
            if(n==null||n.type==BELT)put(x1,y1,BELT,lastDir,0);
        }
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
        float band=42*density,top=124*density,bottom=getHeight()-
                      (selected==0?(TOOLBAR_DP+BELT_MENU_DP*
                          (hasRouteActions()?2f:1f)):TOOLBAR_DP)*density;
        float speed=sz()*7f*dt;
        float sx=gestureX,sy=gestureY;
        if(sx<band)panX+=speed*(1f-sx/band);
        else if(sx>getWidth()-band)panX-=speed*(1f-(getWidth()-sx)/band);
        if(sy<top+band)panY+=speed*(1f-(sy-top)/band);
        else if(sy>bottom-band)panY-=speed*(1f-(bottom-sy)/band);
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
                (selected==5?menuTop:toolbarTop);
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                downX=lastX=gestureX=e.getX();
                downY=lastY=gestureY=e.getY();
                priorGridX=gridX(gestureX);priorGridY=gridY(gestureY);
                toolbarGesture=gestureY>=toolbarTop;
                beltMenuGesture=selected==0&&gestureY>=menuTop&&!toolbarGesture;
                routeMenuGesture=hasRouteActions()&&gestureY>=actionsTop
                    &&gestureY<menuTop&&!toolbarGesture;
                selectionMenuGesture=selected==5&&gestureY>=menuTop&&!toolbarGesture;
                headerGesture=gestureY<116*density;
                upgradeGesture=upgradePanel&&gestureY>=120*density
                    &&gestureY<toolbarTop;
                dragged=false;pinching=false;
                routeGesture=false;paintGesture=false;eraseGesture=false;
                selectionGesture=false;
                if(!toolbarGesture&&!beltMenuGesture&&!routeMenuGesture
                    &&!selectionMenuGesture&&!headerGesture&&!upgradeGesture
                    &&gestureY>=124*density&&gestureY<boardBottom) {
                    if(selected==0) {
                        if(beltMode==0) {
                            paintGesture=true;
                            place(priorGridX,priorGridY);
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
                if(e.getPointerCount()>=2) {
                    pinching=true;dragged=true;lastSpan=span(e);
                    paintGesture=false;routeGesture=false;eraseGesture=false;
                    selectionGesture=false;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
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
                    ||selectionMenuGesture||pinching||upgradeGesture
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
                if(upgradeGesture&&!pinching) {
                    tapUpgradePanel(e.getX(),e.getY());
                } else if(headerGesture&&!pinching) {
                    upgradePanel=!upgradePanel;
                } else if(toolbarGesture&&e.getY()>=toolbarTop) {
                    int index=(int)(e.getX()/Math.max(1f,getWidth()/8f));
                    selected=Math.max(0,Math.min(7,index));
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
                } else if(selectionMenuGesture&&selected==5&&!pinching) {
                    int index=(int)(e.getX()/Math.max(1f,getWidth()/3f));
                    if(index==0&&selectionReady)copySelection();
                    else if(index==1&&selectionReady)eraseSelection();
                    else if(index==2)selectionReady=false;
                } else if(!pinching&&routeGesture&&beltMode==1) {
                    routeTo(new Point(gridX(e.getX()),gridY(e.getY())));
                    routeLive=null; // Remains preview until Confirm.
                } else if(!pinching&&paintGesture) {
                    save();
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
                selectionMenuGesture=false;headerGesture=false;upgradeGesture=false;
                paintGesture=false;routeGesture=false;eraseGesture=false;
                selectionGesture=false;pinching=false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                toolbarGesture=false;beltMenuGesture=false;routeMenuGesture=false;
                selectionMenuGesture=false;headerGesture=false;upgradeGesture=false;
                paintGesture=false;routeGesture=false;eraseGesture=false;
                selectionGesture=false;pinching=false;routeLive=null;
                return true;
            default:return true;
        }
    }
}
