package com.example.arkrecruit.translate;
public class TranslationSmoke {
  public static void main(String[] args){
    TranslationPolicy.Epoch e=new TranslationPolicy.Epoch();long old=e.current();e.advance();if(e.accepts(old))throw new AssertionError("Stale result accepted");
    String a=TranslationPolicy.cacheKey("Hello  Doctor",TranslationPolicy.Kind.UI,"past"),b=TranslationPolicy.cacheKey("Hello Doctor",TranslationPolicy.Kind.UI,"");if(!a.equals(b))throw new AssertionError("UI cache depends on dialogue");
    if(TranslationPolicy.cacheKey("Hello",TranslationPolicy.Kind.STORY,"A").equals(TranslationPolicy.cacheKey("Hello",TranslationPolicy.Kind.STORY,"B")))throw new AssertionError("Dialogue context not isolated");
    String prompt=TranslationPolicy.prompt("Mudrock suffers from Oripathy",TranslationPolicy.Kind.STORY,"Earlier",java.util.Collections.emptyMap());if(!prompt.contains("Мадрок")||!prompt.contains("орипатия")||!prompt.contains("Earlier"))throw new AssertionError("Glossary/context missing");
    if(TranslationPolicy.cacheKey("Hello",TranslationPolicy.Kind.UI,"","ru").equals(TranslationPolicy.cacheKey("Hello",TranslationPolicy.Kind.UI,"","uk")))throw new AssertionError("Target language cache collision");
    String uk=TranslationPolicy.prompt("Oripathy",TranslationPolicy.Kind.UI,"",java.util.Collections.emptyMap(),"uk");
    if(!uk.contains("Ukrainian")||!uk.contains("оріпатія")||!uk.contains("source language automatically"))throw new AssertionError("Ukrainian/auto detection prompt missing");
    TranslationVisibility visible=new TranslationVisibility();
    visible.update(java.util.Arrays.asList("Terminal","Sanity 117","Current"));
    visible.update(java.util.Arrays.asList("Terminal","Sanity 118","Current","animated OCR noise"));
    if(!visible.contains("Terminal"))throw new AssertionError("Changing counter canceled stable menu translation");
    visible.update(java.util.Arrays.asList("Skill","Talent"));
    if(visible.contains("Terminal"))throw new AssertionError("Old-screen result still admitted");
    if(TranslationVisibility.hiddenAfterOcr(true,false,false)||!TranslationVisibility.hiddenAfterOcr(false,false,false)||!TranslationVisibility.hiddenAfterOcr(true,true,false)||!TranslationVisibility.hiddenAfterOcr(true,false,true))throw new AssertionError("OCR completion visibility guard broken");
    if(!"Терминал".equals(InterfaceGlossary.find("Terminal","ru"))||!"Термінал".equals(InterfaceGlossary.find("Terminal","uk"))||InterfaceGlossary.find("A new story line","ru")!=null)throw new AssertionError("Instant labels must respect target and leave new prose to Qwen");
    String[] settings={"Settings","Reset Settings","Game","Volume","Notification","Others","User Center","Game Settings","Maintain 2x Speed","* When enabled, operations will be conducted at 2x speed","Auto–Select Dialogue Option","The first dialogue option will be automatically selected when long pressing to speed up story playback","Dynamic Art–Related Settings","* Only applies to Operators with dynamic art","Home Screen Only","Dynamic Art Entrance Animation","*Entrance animations will no longer be played if turned off (except manual playback)","Assistant Entrance Animation","*Assistant entrance animations will no longer be played if turned off","Every Login","Once per day","Outfit Rotation Frequency","Per day","Per visit","On","Off"};
    for(String label:settings)for(String language:new String[]{"ru","uk"})if(InterfaceGlossary.find(label,language)==null)throw new AssertionError("Settings screen label missing: "+label+"/"+language);
    if(InterfaceGlossary.find("Game settings for an unknown operation","ru")!=null)throw new AssertionError("Partial glossary match swallowed new text");
    TranslationTrace trace=new TranslationTrace();trace.seen("story paragraph","quad");trace.start("story paragraph",1000);String during=trace.report(6000);if(!during.contains("5 с")||!during.contains("Qwen обрабатывает"))throw new AssertionError("Missing live inference duration");
    trace.finish("story paragraph","перевод",7000);if(!trace.report(9000).contains("Ответ: перевод"))throw new AssertionError("Missing actual returned output");trace.error("other","timeout",9000);if(!trace.report(9000).contains("timeout"))throw new AssertionError("Missing failure cause");
    int w=80,h=100;int[] pixels=new int[w*h],shifted=new int[w*h];java.util.Random random=new java.util.Random(19);for(int i=0;i<pixels.length;i++)pixels[i]=random.nextInt(256);for(int y=0;y<h-6;y++)for(int x=0;x<w;x++)shifted[(y+6)*w+x]=pixels[y*w+x];
    MotionTracker.Shift motion=MotionTracker.estimate(pixels,shifted,w,h,null);if(!motion.confident||motion.dy!=6)throw new AssertionError("Scroll shift incorrect");
    int[] unrelated=new int[w*h];for(int i=0;i<unrelated.length;i++)unrelated[i]=random.nextInt(256);if(MotionTracker.estimate(pixels,unrelated,w,h,null).confident)throw new AssertionError("Unrelated screen mistaken for scroll");
    int[] motionA=new int[w*h],motionB=new int[w*h];java.util.Random texture=new java.util.Random(88);
    for(int y=25;y<60;y++)for(int x=15;x<65;x++)motionA[y*w+x]=texture.nextInt(256);
    for(int y=0;y<h;y++)for(int x=0;x<w;x++)motionB[y*w+x]=(x>=3&&y>=2)?motionA[(y-2)*w+x-3]:0;
    RegionalTracker.Pose tracked=RegionalTracker.track(motionA,motionB,w,h,20,30,60,55);
    if(!tracked.reliable||Math.abs(tracked.x(40,40)-43)>.5||Math.abs(tracked.y(40,40)-42)>.5)throw new AssertionError("Per-label motion lagged or lost its displacement");
    RegionalTracker.Pose steady=RegionalTracker.track(motionA,motionA,w,h,20,30,60,55);
    if(!steady.reliable||Math.abs(steady.x(40,40)-40)>.01)throw new AssertionError("Static label jittered");
    java.util.concurrent.ExecutorService owner=java.util.concurrent.Executors.newSingleThreadExecutor();java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicBoolean retired=new java.util.concurrent.atomic.AtomicBoolean();
    owner.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException interrupted){throw new RuntimeException(interrupted);}if(retired.get())throw new AssertionError("Reader closed during owned frame");});
    try{entered.await();com.example.arkrecruit.FrameLifetime.retire(owner,()->retired.set(true));if(retired.get())throw new AssertionError("Retirement bypassed owner queue");release.countDown();owner.shutdown();if(!owner.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS)||!retired.get())throw new AssertionError("Reader not retired after frame completion");}catch(InterruptedException interrupted){throw new RuntimeException(interrupted);}
    trace.start("story paragraph",10000);if(trace.report(11000).contains("Ответ: перевод"))throw new AssertionError("Retry retained stale answer");
    int[] pattern=new int[40*20];for(int i=0;i<pattern.length;i++)pattern[i]=texture.nextInt(256);int[] oldFrame=null;int oldOffset=0;
    for(int frame=0;frame<60;frame++){
      int period=frame%24,offset=period<12?period-6:18-period;int[] next=new int[160*100];
      for(int y=0;y<20;y++)for(int x=0;x<40;x++)next[(y+30)*160+x+30+offset]=pattern[y*40+x];
      for(int y=60;y<80;y++)for(int x=105;x<145;x++)next[y*160+x]=pattern[(y-60)*40+x-105];
      for(int y=0;y<15;y++)for(int x=0;x<160;x++)next[y*160+x]=texture.nextInt(256);
      if(oldFrame!=null){RegionalTracker.Pose moving=RegionalTracker.track(oldFrame,next,160,100,32+oldOffset,32,68+oldOffset,48);RegionalTracker.Pose fixed=RegionalTracker.track(oldFrame,next,160,100,107,62,143,78);
        if(!moving.reliable||Math.abs(moving.x(50+oldOffset,40)-(50+offset))>.6||!fixed.reliable||Math.abs(fixed.x(120,70)-120)>.1)throw new AssertionError("60-frame independent tracking lost moving/static labels");}
      oldFrame=next;oldOffset=offset;
    }

    RegionalTracker.Groups group=new RegionalTracker.Groups();
    for(int frame=0;frame<60;frame++){
      RegionalTracker.Pose left=new RegionalTracker.Pose(1,0,2.1f,1,true),right=new RegionalTracker.Pose(1,0,1.9f,1,true);
      java.util.List<RegionalTracker.Groups.Sample> peers=java.util.Arrays.asList(new RegionalTracker.Groups.Sample("left",30,30,left),new RegionalTracker.Groups.Sample("right",90,30,right),new RegionalTracker.Groups.Sample("third",60,70,new RegionalTracker.Pose(1,0,2,1,true)));
      java.util.Map<String,RegionalTracker.Pose> resolved=group.resolve(peers);
      if(frame>=2&&(resolved.get("left")!=resolved.get("right")||resolved.get("left").a!=1||resolved.get("left").b!=0))throw new AssertionError("Synchronous panel not sharing a transform");
    }
    java.util.Map<String,RegionalTracker.Pose> rescued=group.resolve(java.util.Arrays.asList(new RegionalTracker.Groups.Sample("left",30,30,new RegionalTracker.Pose(1,0,2,1,true)),new RegionalTracker.Groups.Sample("right",90,30,new RegionalTracker.Pose(1,0,2,1,true)),new RegionalTracker.Groups.Sample("third",60,70,new RegionalTracker.Pose(1,0,0,0,false))));
    if(!rescued.get("third").reliable||Math.abs(rescued.get("third").tx-2)>.01)throw new AssertionError("Panel peer cannot bridge a temporary missed label");
    java.util.Map<String,RegionalTracker.Pose> split=group.resolve(java.util.Arrays.asList(new RegionalTracker.Groups.Sample("left",30,30,new RegionalTracker.Pose(1,0,2,1,true)),new RegionalTracker.Groups.Sample("right",90,30,new RegionalTracker.Pose(1,0,-3,-2,true)),new RegionalTracker.Groups.Sample("third",60,70,new RegionalTracker.Pose(1,0,2,1,true))));
    if(split.get("left")==split.get("right")||Math.abs(split.get("right").tx+3)>.01)throw new AssertionError("Independent element remained locked to panel");
    group.clear();
    for(int f=0;f<5;f++){
      java.util.Map<String,RegionalTracker.Pose> scaled=group.resolve(java.util.Arrays.asList(new RegionalTracker.Groups.Sample("a",30,30,new RegionalTracker.Pose(1.01f,.006f,1,2,true)),new RegionalTracker.Groups.Sample("b",100,70,new RegionalTracker.Pose(1.01f,.006f,1,2,true))));
      if(f>=2&&(scaled.get("a")!=scaled.get("b")||Math.abs(scaled.get("a").a-1.01f)>.001))throw new AssertionError("Common scale/rotation not preserved");
    }
    System.out.println("Translation generation/cache/glossary/motion PASS");
  }
}

