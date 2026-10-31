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
    private static final int[][] DEPOSITS={{4,12,1},{5,25,2},{23,8,3},{21,23,4},{-12,8,1},{-12,-8,2},{10,-14,3},{31,-13,5},{-27,22,4}};
    private static final float TOOLBAR_DP=94f, BELT_MENU_DP=44f;
    private final Map<Long,Node> cells = new HashMap<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final SharedPreferences prefs;
    private final float density;
    private float panX,panY,tile,zoom=.70f;
    private float clock,stepAccumulator,sourceAccumulator,flash;
    private long lastFrame;
    private int selected=7, level=1,goal=1,required=10,delivered,allDelivered;
    private int beltMode=0, selectX0,selectY0,selectX1,selectY1,clipboardCount;
    private boolean initialized,pinching,dragged,toolbarGesture,beltMenuGesture,routeGesture,paintGesture;
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
    private int depositValue(int x,int y) {
        for(int[] dep:DEPOSITS) if(dep[0]==x && dep[1]==y) return dep[2];
        int gx=Math.floorDiv(x,24),gy=Math.floorDiv(y,24);
        if(Math.abs(gx)<2 && Math.abs(gy)<2) return -1;
        int h=(gx*73856093)^(gy*19349663)^0x13579bdf;
        int cx=gx*24+7+Math.floorMod(h,9);
        int cy=gy*24+7+Math.floorMod(h>>8,9);
        return x==cx && y==cy?1+Math.floorMod(h>>16,9):-1;
    }
    private int depositUnder(int x,int y) {
        for(int dy=-1;dy<=1;dy++) for(int dx=-1;dx<=1;dx++) {
            int val=depositValue(x+dx,y+dy);
            if(val>0)return val;
        }
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
        for(int[] dep:DEPOSITS)drawDeposit(c,dep[0],dep[1],dep[2]);
        float s=sz();
        int gxMin=Math.max(-100,Math.floorDiv(gridX(0)-24,24));
        int gxMax=Math.min(100,Math.floorDiv(gridX(getWidth())+24,24));
        int gyMin=Math.max(-100,Math.floorDiv(gridY(0)-24,24));
        int gyMax=Math.min(100,Math.floorDiv(gridY(getHeight())+24,24));
        for(int gy=gyMin;gy<=gyMax;gy++)for(int gx=gxMin;gx<=gxMax;gx++) {
            if(Math.abs(gx)<2&&Math.abs(gy)<2)continue;
            int h=(gx*73856093)^(gy*19349663)^0x13579bdf;
            drawDeposit(c,gx*24+7+Math.floorMod(h,9),
                    gy*24+7+Math.floorMod(h>>8,9),
                    1+Math.floorMod(h>>16,9));
        }
    }
    private void drawHub(Canvas c) {
        float s=sz(),left=panX+HUB_X*s,top=panY+HUB_Y*s,side=HUB_SIZE*s;
        if(left>getWidth()||top>getHeight()||left+side<0||top+side<0)return;
        box(c,left,top,left+side,top+side,s*.55f,0xff154b60);
        box(c,left+s*.20f,top+s*.20f,left+side-s*.20f,top+side-s*.20f,
                s*.45f,0xff3ed1c8);
        box(c,left+s*.42f,top+s*.42f,left+side-s*.42f,top+side-s*.42f,
                s*.38f,0xff123548);
        label(c,"◎",left+side*.5f,top+side*.57f,
             s*2.5f,0xffb5fff2,true,Paint.Align.CENTER);
        label(c,"БАЗА 6×6",left+side*.5f,top+side*.75f,
             s*.56f,0xffabf5e9,true,Paint.Align.CENTER);
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
            int maxOutputs=Math.min(4,upgradeLevels[1]);
            // Extractor saturates several independent output belts when upgraded.
            for(int i=0;i<maxOutputs;i++) {
                int d=(n.dir+i)%4;
                Node out=get(x+DX[d]*2,y+DY[d]*2);
                if(out!=null&&out.type==BELT&&out.item==EMPTY) {
                    out.item=n.value;n.extracted++;
                }
            }
        }
        for(Node n:cells.values()) {
            if((n.type==ADD || n.type==MULTIPLY) &&
               n.a!=EMPTY && n.b!=EMPTY && n.item==EMPTY) {
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
        float cx=panX+13.5f*s,cy=panY+9.5f*s;
        for(int k=4;k>0;k--) {
            p.setColor(Color.argb(9,55,200,194));
            c.drawCircle(cx,cy,s*(1.0f+k*.55f),p);
        }
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
            // Inbound direction allows actual elbow rendering, not a straight rotated tile.
            for(int candidate=0;candidate<4;candidate++) {
                int px=x-DX[candidate],py=y-DY[candidate];
                Node prev=get(px,py);
                if(prev!=null && prev.dir==candidate &&
                      (prev.type==BELT||prev.type==SOURCE||prev.type==ADD||prev.type==MULTIPLY)) {
                    incoming=candidate;break;
                }
            }
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            Path bend=new Path();
            bend.moveTo(cx-DX[incoming]*s*.48f,cy-DY[incoming]*s*.48f);
            bend.lineTo(cx,cy);
            bend.lineTo(cx+DX[n.dir]*s*.48f,cy+DY[n.dir]*s*.48f);
            p.setColor(0xff123346);p.setStrokeWidth(s*.52f);
            c.drawPath(bend,p);
            p.setColor(0xff277a85);p.setStrokeWidth(s*.38f);
            c.drawPath(bend,p);
            p.setColor(0xff5ae3d4);p.setStrokeWidth(Math.max(1f,s*.055f));
            c.drawPath(bend,p);
            p.setStyle(Paint.Style.FILL);
            float arrowX=cx+DX[n.dir]*s*.22f,arrowY=cy+DY[n.dir]*s*.22f;
            label(c,n.dir==0?"›":n.dir==1?"⌄":n.dir==2?"‹":"⌃",
                  arrowX,arrowY+s*.12f,s*.42f,0xffd2fff6,true,Paint.Align.CENTER);
        } else if(n.type==SOURCE) {
            box(c,cx-s*.45f,cy-s*.45f,cx+s*.45f,cy+s*.45f,
                s*.13f,0xff5d3e37);
            box(c,cx-s*.40f,cy-s*.40f,cx+s*.40f,cy+s*.40f,
                s*.11f,0xffd98b4a);
            box(c,cx-s*.33f,cy-s*.31f,cx+s*.33f,cy+s*.04f,
                s*.08f,0xfff7c478);
            label(c,Integer.toString(n.value),cx,cy+s*.01f,
                  s*.39f,0xff302331,true,Paint.Align.CENTER);
            label(c,"ДОБЫЧА "+upgradeLevels[1], cx,cy+s*.25f,s*.115f,
                  0xff432b27,true,Paint.Align.CENTER);
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
            cells.remove(id(x,y));
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
    }
    private void drawSelection(Canvas c) {
        if(selected!=5)return;
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
    private void drawBeltMenu(Canvas c) {
        float w=getWidth(),d=density,top=getHeight()-(TOOLBAR_DP+BELT_MENU_DP)*d;
        box(c,6*d,top,w-6*d,top+BELT_MENU_DP*d,12*d,0xff173448);
        String[] captions={"Рисовать","Протяжка","Подтвердить","Отмена"};
        for(int i=0;i<4;i++) {
            float l=w*i/4f,r=w*(i+1)/4f;
            if((i==0&&beltMode==0)||(i==1&&beltMode==1))
                box(c,l+4*d,top+3*d,r-4*d,top+41*d,9*d,0xff297679);
            label(c,captions[i],(l+r)*.5f,top+27*d,11*d,
                0xffe3fff7,true,Paint.Align.CENTER);
        }
    }
    private void updateEdgeScroll(float dt) {
        if(!(paintGesture||routeGesture)||pinching||toolbarGesture)return;
        float band=42*density,top=124*density,bottom=getHeight()-
                      (selected==0?(TOOLBAR_DP+BELT_MENU_DP):TOOLBAR_DP)*density;
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
        }
    }
    private static float span(MotionEvent e) {
        return (float)Math.hypot(e.getX(0)-e.getX(1),
                                 e.getY(0)-e.getY(1));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int action=e.getActionMasked();
        float toolTop=getHeight()-TOOLBAR_DP*density;
        float menuTop=toolTop-BELT_MENU_DP*density;
        float touchBottom=selected==0?menuTop:toolTop;
        switch(action) {
            case MotionEvent.ACTION_DOWN:
                downX=lastX=gestureX=e.getX();
                downY=lastY=gestureY=e.getY();
                priorGridX=gridX(gestureX);priorGridY=gridY(gestureY);
                toolbarGesture=gestureY>=toolTop;
                beltMenuGesture=selected==0&&gestureY>=menuTop&&!toolbarGesture;
                pinching=false;dragged=false;routeGesture=false;paintGesture=false;
                upgradeGesture=upgradePanel&&gestureY>=120*density;
                headerGesture=gestureY<116*density;
                if(!upgradeGesture&&!headerGesture&&!toolbarGesture&&!beltMenuGesture&&gestureY>=124*density) {
                    if(selected==0) {
                        if(beltMode==0) {
                            paintGesture=true;
                            place(priorGridX,priorGridY);
                        } else {
                            routeGesture=true;
                            if(route.isEmpty())route.add(new Point(priorGridX,priorGridY));
                            routeLive=new Point(priorGridX,priorGridY);
                        }
                    } else if(selected==5) {
                        selectX0=selectX1=priorGridX;
                        selectY0=selectY1=priorGridY;
                    }
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if(e.getPointerCount()>=2) {
                    pinching=true;dragged=true;lastSpan=span(e);
                    paintGesture=false;routeGesture=false;
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
                if(toolbarGesture||beltMenuGesture||pinching||upgradeGesture||headerGesture)return true;
                gestureX=e.getX();gestureY=e.getY();
                if(selected==7) {
                    panX+=gestureX-lastX;panY+=gestureY-lastY;dragged=true;
                } else if(selected==0) {
                    int gx=gridX(gestureX),gy=gridY(gestureY);
                    if(beltMode==0&&paintGesture&&(gx!=priorGridX||gy!=priorGridY)) {
                        drawBeltPath(priorGridX,priorGridY,gx,gy);
                        priorGridX=gx;priorGridY=gy;dragged=true;
                    } else if(beltMode==1&&routeGesture) {
                        routeLive=new Point(gx,gy);dragged=true;
                    }
                } else if(selected==5) {
                    selectX1=gridX(gestureX);selectY1=gridY(gestureY);
                    dragged=true;
                } else if(Math.hypot(gestureX-downX,gestureY-downY)>8*density){
                    dragged=true;panX+=gestureX-lastX;panY+=gestureY-lastY;
                }
                lastX=gestureX;lastY=gestureY;
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                pinching=true;return true;
            case MotionEvent.ACTION_UP:
                if(upgradeGesture&&!pinching) {
                    tapUpgradePanel(e.getX(),e.getY());
                } else if(headerGesture&&!pinching) {
                    upgradePanel=!upgradePanel;
                } else if(toolbarGesture&&e.getY()>=toolTop) {
                    int index=(int)(e.getX()/Math.max(1f,getWidth()/8f));
                    selected=Math.max(0,Math.min(7,index));
                } else if(beltMenuGesture&&selected==0) {
                    int button=(int)(e.getX()/Math.max(1f,getWidth()/4f));
                    if(button==0)beltMode=0;
                    else if(button==1)beltMode=1;
                    else if(button==2)confirmRoute();
                    else if(button==3){route.clear();routeLive=null;}
                } else if(!pinching && routeGesture && beltMode==1) {
                    routeTo(routeLive);
                    routeLive=null; // Lifting the finger NEVER installs the preview.
                } else if(!pinching && paintGesture) {
                    save();
                } else if(!pinching && selected==5) {
                    selectX1=gridX(e.getX());selectY1=gridY(e.getY());
                    copySelection();
                } else if(!pinching&&!dragged&&selected!=7 &&
                          e.getY()>124*density&&e.getY()<touchBottom) {
                    place(gridX(e.getX()),gridY(e.getY()));
                }
                toolbarGesture=false;beltMenuGesture=false;
                upgradeGesture=false;headerGesture=false;
                paintGesture=false;routeGesture=false;pinching=false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                toolbarGesture=false;beltMenuGesture=false;
                paintGesture=false;routeGesture=false;pinching=false;
                routeLive=null;return true;
            default:return true;
        }
    }
}
