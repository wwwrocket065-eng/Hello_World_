package com.numera.factory;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

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
    private static final String[] TOOLS={"Лента","Источник","Сумма","Умнож.","Удалить","Камера"};
    private static final String[] ICONS={"➜","◈","+","×","⌫","✥"};
    private final Map<Long,Node> cells = new HashMap<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final SharedPreferences prefs;
    private final float density;
    private float panX,panY,tile,zoom=1f;
    private float clock,stepAccumulator,sourceAccumulator,flash;
    private long lastFrame;
    private int selected=0, level=1,goal=5,required=12,delivered;
    private boolean initialized,pinching,dragged,toolbarGesture;
    private float lastX,lastY,lastSpan,downX,downY;
    private int priorGridX,priorGridY;

    private static final class Node {
        int type,dir,value,item=EMPTY,a=EMPTY,b=EMPTY;
        Node(int type,int dir,int value) {
            this.type=type;this.dir=dir;this.value=value;
        }
    }

    public FactoryView(Context ctx) {
        super(ctx);
        density=getResources().getDisplayMetrics().density;
        prefs=ctx.getSharedPreferences("numera_world_v1",Context.MODE_PRIVATE);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
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
        level=1;goal=5;required=12;delivered=0;
        put(2,7,SOURCE,0,2);
        put(2,11,SOURCE,0,3);
        put(2,15,SOURCE,0,5);
        for(int x=3;x<=7;x++) {put(x,7,BELT,0,0);put(x,11,BELT,0,0);}
        put(8,7,BELT,1,0); put(8,8,BELT,1,0);
        put(8,11,BELT,3,0); put(8,10,BELT,3,0);
        put(8,9,ADD,0,0);
        for(int x=9;x<=12;x++) put(x,9,BELT,0,0);
        put(13,9,HUB,0,0);
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
            cells.clear();
            for(String record:parts[1].split(";")) {
                if(record.isEmpty()) continue;
                String[] v=record.split(",");
                put(Integer.parseInt(v[0]),Integer.parseInt(v[1]),
                    Integer.parseInt(v[2]),Integer.parseInt(v[3]),Integer.parseInt(v[4]));
            }
            if(cells.isEmpty()) seed();
        } catch(Exception ex) {seed();}
    }
    public void save() {
        StringBuilder b=new StringBuilder();
        b.append(level).append(',').append(goal).append(',')
            .append(required).append(',').append(delivered).append('#');
        for(Map.Entry<Long,Node> entry:cells.entrySet()) {
            long key=entry.getKey();
            Node n=entry.getValue();
            b.append((int)(key>>32)).append(',').append((int)key).append(',')
                .append(n.type).append(',').append(n.dir).append(',')
                .append(n.value).append(';');
        }
        prefs.edit().putString("world",b.toString()).apply();
    }

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        super.onSizeChanged(w,h,oldW,oldH);
        float oldTile=tile;
        tile=w/11.9f;
        if(!initialized) {
            panX=w*.52f-8.1f*tile;
            panY=h*.53f-9.1f*tile;
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
            sourceAccumulator+=dt;
            if(sourceAccumulator>=.95f) {
                sourceAccumulator-=.95f;
                for(Node n:cells.values()) {
                    if(n.type==SOURCE && n.item==EMPTY) n.item=n.value;
                }
            }
            stepAccumulator+=dt;
            if(stepAccumulator>=.19f) {
                stepAccumulator-=.19f;
                step();
            }
        }
        lastFrame=now;
        c.drawColor(BG);
        drawGrid(c);
        float hudH=124*density;
        float barY=getHeight()-94*density;
        c.save();
        c.clipRect(0,hudH,getWidth(),barY);
        for(Map.Entry<Long,Node> entry:cells.entrySet()) {
            long key=entry.getKey();
            drawNode(c,(int)(key>>32),(int)key,entry.getValue());
        }
        c.restore();
        drawHud(c);
        drawToolbar(c);
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
            int nx=x+DX[d],ny=y+DY[d];
            long next=id(nx,ny);
            Node dest=cells.get(next);
            if(dest==null)continue;
            if(dest.type==HUB) {
                if(n.item==goal) {
                    delivered++;
                    flash=1f;
                    if(delivered>=required) {
                        level++;
                        int[] sequence={5,8,12,20,24,30,42,50,64,81};
                        goal=sequence[(level-1)%sequence.length];
                        required=12+level*6;
                        delivered=0;
                        save();
                    }
                }
                n.item=EMPTY;
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
        } else if(n.type==SOURCE) {
            box(c,cx-s*.45f,cy-s*.45f,cx+s*.45f,cy+s*.45f,
                s*.13f,0xff5d3e37);
            box(c,cx-s*.40f,cy-s*.40f,cx+s*.40f,cy+s*.40f,
                s*.11f,0xffd98b4a);
            box(c,cx-s*.33f,cy-s*.31f,cx+s*.33f,cy+s*.04f,
                s*.08f,0xfff7c478);
            label(c,Integer.toString(n.value),cx,cy+s*.01f,
                  s*.39f,0xff302331,true,Paint.Align.CENTER);
            label(c,"ИСТОЧНИК",cx,cy+s*.25f,s*.115f,
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
    private void drawToolbar(Canvas c) {
        float d=density,w=getWidth(),h=getHeight();
        float top=h-94*d;
        box(c,6*d,top,w-6*d,h+18*d,19*d,0xff0c2232);
        float padding=11*d;
        float itemW=(w-2*padding)/6f;
        for(int i=0;i<6;i++) {
            float l=padding+i*itemW,mid=l+itemW*.5f;
            boolean on=selected==i;
            if(on) {
                box(c,l+2*d,top+7*d,l+itemW-2*d,h-8*d,
                    13*d,0xff20505c);
                box(c,l+9*d,top+7*d,l+itemW-9*d,top+10*d,
                    2*d,CYAN);
            }
            label(c,ICONS[i],mid,top+47*d,28*d,
                  on?0xffd3ffef:0xffa8c0cd,true,Paint.Align.CENTER);
            label(c,TOOLS[i],mid,top+69*d,9*d,
                  on?0xffd4fff9:0xff8ba6b3,false,Paint.Align.CENTER);
        }
    }

    private int gridX(float screenX) {return (int)Math.floor(worldX(screenX));}
    private int gridY(float screenY) {return (int)Math.floor(worldY(screenY));}
    private void place(int x,int y) {
        if(x<0||y<0||x>80||y>80)return;
        Node old=get(x,y);
        if(old!=null && old.type==HUB)return;
        if(selected==0) {
            if(old!=null&&old.type==BELT)old.dir=(old.dir+1)%4;
            else put(x,y,BELT,0,0);
        } else if(selected==1) {
            int v=1+Math.abs((x*31+y*17)%9);
            put(x,y,SOURCE,0,v);
        } else if(selected==2) put(x,y,ADD,0,0);
        else if(selected==3) put(x,y,MULTIPLY,0,0);
        else if(selected==4) cells.remove(id(x,y));
        save();
    }
    private void drawBeltPath(int x0,int y0,int x1,int y1) {
        if(x0<0||y0<0||x1<0||y1<0||
           x0>80||y0>80||x1>80||y1>80)return;
        // Most touch events traverse one cell; interpolate longer strokes.
        int x=x0,y=y0;
        int guard=0;
        while((x!=x1||y!=y1) && guard++<200) {
            int dx=0,dy=0;
            if(x!=x1)dx=Integer.signum(x1-x);
            else dy=Integer.signum(y1-y);
            int dir=dx==1?0:dx==-1?2:dy==1?1:3;
            Node n=get(x,y);
            if(n==null||n.type==BELT)put(x,y,BELT,dir,0);
            x+=dx;y+=dy;
        }
        if(get(x1,y1)==null)put(x1,y1,BELT,0,0);
        save();
    }
    private static float span(MotionEvent e) {
        return (float)Math.hypot(e.getX(0)-e.getX(1),
                                 e.getY(0)-e.getY(1));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=lastX=e.getX();downY=lastY=e.getY();
                priorGridX=gridX(lastX);priorGridY=gridY(lastY);
                toolbarGesture=lastY>getHeight()-94*density;
                dragged=false;pinching=false;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if(e.getPointerCount()>=2) {
                    pinching=true;dragged=true;lastSpan=span(e);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if(e.getPointerCount()>=2) {
                    float current=span(e);
                    if(lastSpan>5f&&current>5f) {
                        float midX=(e.getX(0)+e.getX(1))*.5f;
                        float midY=(e.getY(0)+e.getY(1))*.5f;
                        float wx=worldX(midX),wy=worldY(midY);
                        zoom=Math.max(.45f,Math.min(2.5f,zoom*current/lastSpan));
                        panX=midX-wx*sz();panY=midY-wy*sz();
                    }
                    lastSpan=current;return true;
                }
                if(toolbarGesture||pinching)return true;
                float x=e.getX(),y=e.getY();
                if(selected==5) {
                    panX+=x-lastX;panY+=y-lastY;dragged=true;
                } else if(selected==0) {
                    int gx=gridX(x),gy=gridY(y);
                    if(gx!=priorGridX||gy!=priorGridY) {
                        drawBeltPath(priorGridX,priorGridY,gx,gy);
                        priorGridX=gx;priorGridY=gy;dragged=true;
                    }
                } else if(Math.hypot(x-downX,y-downY)>8*density) {
                    dragged=true;
                    panX+=x-lastX;panY+=y-lastY;
                }
                lastX=x;lastY=y;
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                pinching=true;return true;
            case MotionEvent.ACTION_UP:
                if(toolbarGesture) {
                    if(e.getY()>getHeight()-94*density) {
                        float width=(getWidth()-22*density)/6f;
                        int index=(int)((e.getX()-11*density)/width);
                        selected=Math.max(0,Math.min(5,index));
                    }
                } else if(!pinching&&!dragged&&selected!=5 &&
                          e.getY()>124*density &&
                          e.getY()<getHeight()-94*density) {
                    place(gridX(e.getX()),gridY(e.getY()));
                }
                pinching=false;toolbarGesture=false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                pinching=false;toolbarGesture=false;
                return true;
            default:return true;
        }
    }
}
