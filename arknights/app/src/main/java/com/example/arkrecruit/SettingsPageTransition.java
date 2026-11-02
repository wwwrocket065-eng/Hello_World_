package com.example.arkrecruit;

import android.graphics.Color;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.*;

/** Horizontal push/pop, with an independently scrolling child page. */
public final class SettingsPageTransition {
  private SettingsPageTransition(){}
  public static Session open(LinearLayout host,LinearLayout page){return new Session(host,page);}
  public static final class Session {
    private final LinearLayout host,oldContent;
    private final ArrayList<View> previous=new ArrayList<>();
    private final FrameLayout stage;
    private final ScrollView oldPage,newPage;
    private final int oldScroll;
    private boolean closing;
    private final ScrollView outer;
    Session(LinearLayout host,LinearLayout page){
      this.host=host;
      outer=host.getParent() instanceof ScrollView?(ScrollView)host.getParent():null;
      oldScroll=outer==null?0:outer.getScrollY();
      int height=outer!=null?outer.getHeight()-host.getPaddingTop()-host.getPaddingBottom():host.getHeight();
      for(int i=0;i<host.getChildCount();i++)previous.add(host.getChildAt(i));
      host.removeAllViews();
      stage=new FrameLayout(host.getContext());stage.setClipChildren(true);stage.setBackground(host.getBackground()==null?new android.graphics.drawable.ColorDrawable(UiStyle.BACKGROUND):host.getBackground().getConstantState()==null?new android.graphics.drawable.ColorDrawable(UiStyle.SURFACE):host.getBackground().getConstantState().newDrawable());
      host.addView(stage,new LinearLayout.LayoutParams(-1,Math.max(UiStyle.dp(host.getContext(),160),height)));
      oldContent=new LinearLayout(host.getContext());oldContent.setOrientation(LinearLayout.VERTICAL);
      for(View view:previous)oldContent.addView(view);
      oldPage=new ScrollView(host.getContext());oldPage.addView(oldContent);stage.addView(oldPage,new FrameLayout.LayoutParams(-1,-1));
      newPage=new ScrollView(host.getContext());newPage.setFillViewport(true);newPage.setBackground(host.getBackground()==null?new android.graphics.drawable.ColorDrawable(UiStyle.BACKGROUND):host.getBackground().getConstantState()==null?new android.graphics.drawable.ColorDrawable(UiStyle.SURFACE):host.getBackground().getConstantState().newDrawable());newPage.addView(page);
      stage.addView(newPage,new FrameLayout.LayoutParams(-1,-1));
      if(outer!=null)outer.scrollTo(0,0);
      stage.post(()->{
        if(closing)return;
        oldPage.scrollTo(0,oldScroll);
        float width=stage.getWidth();newPage.setTranslationX(width);
        newPage.animate().translationX(0).setDuration(280).setInterpolator(new DecelerateInterpolator(1.4f)).start();
        oldPage.animate().translationX(-width*.25f).alpha(.8f).setDuration(280).withEndAction(()->{if(!closing)oldPage.setVisibility(View.GONE);}).start();
      });
    }
    public void close(Runnable done){
      if(closing)return;closing=true;
      oldPage.animate().cancel();newPage.animate().cancel();oldPage.setVisibility(View.VISIBLE);
      oldPage.animate().translationX(0).alpha(1).setDuration(260).start();
      newPage.animate().translationX(stage.getWidth()).setDuration(260).setInterpolator(new DecelerateInterpolator(1.4f)).withEndAction(()->{
        oldContent.removeAllViews();host.removeAllViews();for(View view:previous)host.addView(view);
        if(outer!=null)outer.post(()->outer.scrollTo(0,oldScroll));
        if(done!=null)done.run();
      }).start();
    }
  }
}
