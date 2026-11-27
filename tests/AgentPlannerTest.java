package local.airis.agents;
import java.util.*;
public final class AgentPlannerTest {
 public static void main(String[] args){
  List<Integer> all=Arrays.asList(0,1,2,7);
  if(!AgentPlanner.route(all,"Напиши игру змейка",true).equals(Arrays.asList(1)))throw new AssertionError("coder");
  if(!AgentPlanner.route(all,"Исправь код и проверь",true).equals(Arrays.asList(1,2)))throw new AssertionError("review");
  if(!AgentPlanner.route(all,"Все агенты обсудите задачу",true).equals(all))throw new AssertionError("all");
  if(!AgentPlanner.route(all,"Привет",false).equals(all))throw new AssertionError("manual");
  if(!AgentPlanner.files("Скиньте ссылку на скачивание змейки"))throw new AssertionError("file request");
  if(!AgentPlanner.artifact("Привет вы сможете написать игру змейка"))throw new AssertionError("artifact");
  if(AgentPlanner.title("один два три четыре пять шесть семь").contains("семь"))throw new AssertionError("title");
 }
}