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
    int w=80,h=100;int[] pixels=new int[w*h],shifted=new int[w*h];java.util.Random random=new java.util.Random(19);for(int i=0;i<pixels.length;i++)pixels[i]=random.nextInt(256);for(int y=0;y<h-6;y++)for(int x=0;x<w;x++)shifted[(y+6)*w+x]=pixels[y*w+x];
    MotionTracker.Shift motion=MotionTracker.estimate(pixels,shifted,w,h,null);if(!motion.confident||motion.dy!=6)throw new AssertionError("Scroll shift incorrect");
    int[] unrelated=new int[w*h];for(int i=0;i<unrelated.length;i++)unrelated[i]=random.nextInt(256);if(MotionTracker.estimate(pixels,unrelated,w,h,null).confident)throw new AssertionError("Unrelated screen mistaken for scroll");
    System.out.println("Translation generation/cache/glossary/motion PASS");
  }
}

