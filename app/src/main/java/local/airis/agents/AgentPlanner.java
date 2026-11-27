package local.airis.agents;
import java.util.*;
final class AgentPlanner {
    static String lower(String s){return s.toLowerCase(Locale.ROOT);}
    static boolean coding(String s){return lower(s).matches("(?s).*(код|программ|игр[уыа]|приложени|скрипт|html|javascript|python|main\\.js|index\\.html|apk|репозитори|сайт).*");}
    static boolean artifact(String s){String t=lower(s);return coding(t)&&t.matches("(?s).*(напиш|напис|созда|сдела|реализ|исправ|добав|разработ|сгенерир|доработ|собер|оптимиз|перепиш|улучш|почин|измени).*");}
    static boolean files(String s){String t=lower(s);return t.matches("(?s).*(ссылк|скача|скин|отправ|дай|покаж).*")&&t.matches("(?s).*(файл|код|игр|змейк|приложен|результат|html|\\.js|apk).*")&&!artifact(t);}
    static boolean collective(String s){return lower(s).matches("(?s).*(все агенты|всех агентов|всем агентам|каждый агент|каждого агента|вся команда|всей командой|все выбранные|общее обсуждение|дебаты).*");}
    static boolean review(String s){return lower(s).matches("(?s).*(проверь|протестируй|тестирован|ревью|аудит|проверка|безопасност|сложн|архитектур|оптимиз).*");}
    static int preferred(List<Integer> a,int... roles){for(int role:roles)if(a.contains(role))return role;return a.get(0);}
    static ArrayList<Integer> route(List<Integer> chosen,String task,boolean fast){
        ArrayList<Integer> out=new ArrayList<>();
        if(chosen.isEmpty())return out;
        if(!fast||collective(task)){out.addAll(chosen);return out;}
        if(artifact(task)){
            int lead=preferred(chosen,1,3,7,0,2,4);out.add(lead);
            if(review(task)){int check=preferred(chosen,2,4,6,7,3);if(check!=lead)out.add(check);}
        }else if(coding(task)){out.add(preferred(chosen,1,3,7,2,0));}
        else if(review(task)){out.add(preferred(chosen,2,4,6,7,3,0));}
        else{out.add(preferred(chosen,0,5,9,3));}
        return out;
    }
    static String title(String task){String t=task.trim().replaceAll("\\s+"," ");String[] words=t.split(" ");StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(words.length,6);i++){if(i>0)b.append(' ');b.append(words[i]);}String out=b.toString();return out.substring(0,Math.min(80,out.length()));}
}