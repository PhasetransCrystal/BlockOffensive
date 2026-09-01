import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public class ParseSpark {
  static Object call(Object o, String n, Class<?>... p) throws Exception { return o.getClass().getMethod(n, p).invoke(o); }
  static void dump(Object o, String key, int depth) throws Exception {
    if (depth > 5) { System.out.println(key+": ..."); return; }
    Class<?> c=o.getClass();
    if (o instanceof String || o instanceof Number || o instanceof Boolean || c.isEnum()) { System.out.println(key+": "+o); return; }
    if (o instanceof List<?> l) { System.out.println(key+" ["+l.size()+"]"); int i=0; for(Object x:l) dump(x,"  "+i++,depth+1); return; }
    if (o instanceof Map<?,?> m) { System.out.println(key+" map["+m.size()+"]"); for(var e:m.entrySet()) dump(e.getValue(),"  "+e.getKey(),depth+1); return; }
    try {
      var desc=(java.util.Map<?,?>)call(o,"getAllFields");
      System.out.println(key+" {");
      for(var e:desc.entrySet()) dump(e.getValue(), String.valueOf(call(e.getKey(),"getName")), depth+1);
      System.out.println("}");
    } catch(Exception e) { System.out.println(key+": "+o); }
  }
  static double sum(List<?> l){double s=0;for(Object x:l)s+=((Number)x).doubleValue();return s;}
  public static void main(String[] a) throws Exception {
    Class<?> cls=Class.forName("me.lucko.spark.proto.SparkSamplerProtos$SamplerData");
    Method parse=cls.getMethod("parseFrom", InputStream.class);
    Object data=parse.invoke(null,new FileInputStream(a[0]));
    Object md=call(data,"getMetadata");
    dump(md,"metadata",0);
    System.out.println("TIME_WINDOWS "+call(data,"getTimeWindowsList"));
    System.out.println("WINDOW_STATS"); dump(call(data,"getTimeWindowStatisticsMap"),"window",0);
    List<?> threads=(List<?>)call(data,"getThreadsList");
    System.out.println("THREADS "+threads.size());
    for(Object t:threads){String name=(String)call(t,"getName"); List<?> times=(List<?>)call(t,"getTimesList"); double total=sum(times); System.out.printf("THREAD %s samples=%d total=%.0f avg=%.2f%n",name,times.size(),total,times.isEmpty()?0:total/times.size()); dumpChildren(t,"",0, total);}
  }
  static void dumpChildren(Object node,String indent,int depth,double parent) throws Exception {
    if (node.getClass().getName().contains("ThreadNode")) { dumpFlat((List<?>)call(node,"getChildrenList"), indent, depth, parent); return; }
    if(depth>3)return;
    List<?> ch=(List<?>)call(node,"getChildrenList");
    record Item(String n,double t,Object o){}
    ArrayList<Item> arr=new ArrayList<>();
    for(Object x:ch){String n=(String)call(x,"getClassName")+"."+(String)call(x,"getMethodName"); double t=sum((List<?>)call(x,"getTimesList")); arr.add(new Item(n,t,x));}
    arr.sort((x,y)->Double.compare(y.t,x.t));
    int lim=Math.min(15,arr.size());
    for(int i=0;i<lim;i++){Item x=arr.get(i);System.out.printf("%s%s %.0f (%.1f%%)%n",indent,x.n,x.t,parent==0?0:100*x.t/parent);dumpChildren(x.o,indent+"  ",depth+1,x.t);}
  }
  static void dumpFlat(List<?> roots,String indent,int depth,double parent) throws Exception {
    ArrayList<Object> all=new ArrayList<>(roots);
    int maxRef=0; for(Object r:roots) for(Object q:(List<?>)call(r,"getChildrenRefsList")) maxRef=Math.max(maxRef,((Number)q).intValue()); System.out.println("ROOTLIST "+all.size()+" MAXREF "+maxRef);
    ArrayList<Integer> rootIds=new ArrayList<>(); for(Object r:roots) rootIds.add(all.indexOf(r));
    showIds(all,rootIds,indent,depth,parent,new HashSet<>());
  }
  static void flatten(Object n,List<Object> all) throws Exception { if(all.contains(n))return; all.add(n); for(Object x:(List<?>)call(n,"getChildrenRefsList")){} }
  static void showIds(List<Object> all,List<Integer> ids,String indent,int depth,double parent,Set<Integer> seen) throws Exception {
    if(depth>6)return; ArrayList<Integer> sorted=new ArrayList<>(ids); sorted.sort((a,b)->{try{return Double.compare(sum((List<?>)call(all.get(b),"getTimesList")),sum((List<?>)call(all.get(a),"getTimesList")));}catch(Exception e){return 0;}});
    for(int id:sorted){if(!seen.add(id))continue; Object x=all.get(id); String n=(String)call(x,"getClassName")+"."+(String)call(x,"getMethodName"); double t=sum((List<?>)call(x,"getTimesList")); System.out.printf("%s%s %.0f (%.1f%%)%n",indent,n,t,parent==0?0:100*t/parent); List<Integer> refs=new ArrayList<>(); for(Object q:(List<?>)call(x,"getChildrenRefsList")) refs.add(((Number)q).intValue()); showIds(all,refs,indent+"  ",depth+1,t,seen); }
  }
}
