package app.lensdrop.transfer;
public class PrintProfilePolicyTest {
 static int count;static void check(boolean ok){count++;if(!ok)throw new AssertionError("case "+count);}static void bad(Runnable r){boolean fail=false;try{r.run();}catch(IllegalArgumentException e){fail=true;}check(fail);}
 public static void main(String[] args){String auto="cp9550-strip-pair-auto",manual="cp9550-strip-pair-manual",photo="cp9550-photo-4x6";
 check(PrintProfilePolicy.isPair(auto));check(PrintProfilePolicy.isPair(manual));check(!PrintProfilePolicy.isPair(photo));check(!PrintProfilePolicy.isPair("unknown"));
 for(int n=1;n<=5;n++){check(PrintProfilePolicy.pieces(auto,n)==2*n);check(PrintProfilePolicy.pieces(photo,n)==n);}bad(()->PrintProfilePolicy.pieces(auto,0));bad(()->PrintProfilePolicy.pieces(auto,6));
 double[][] p=PrintProfilePolicy.plan(auto,600,1800,400,600);check(p.length==2);check(p[0][0]==0&&p[0][2]==200&&p[0][3]==600);check(p[1][0]==200&&p[1][1]==0&&p[1][4]==0);
 double[][] l=PrintProfilePolicy.plan(auto,600,1800,600,400);check(l[0][4]==1);check(l[0][2]==600&&l[0][3]==200&&l[1][1]==200);
 double[][] r=PrintProfilePolicy.plan(manual,1800,600,400,600);check(r[0][4]==1);check(r[1][0]==200);
 bad(()->PrintProfilePolicy.plan(auto,1200,1800,400,600));bad(()->PrintProfilePolicy.plan(auto,1200,1800,600,400));bad(()->PrintProfilePolicy.plan(auto,0,1800,400,600));bad(()->PrintProfilePolicy.plan(photo,600,1800,0,600));
 check(PrintProfilePolicy.plan(photo,1800,1200,400,600)[0][4]==1);check(PrintProfilePolicy.plan(photo,1200,1800,400,600)[0][4]==0);
 check(PrintProfilePolicy.copyLabel(auto,2).contains("4 strips"));check(PrintProfilePolicy.copyLabel(photo,2).equals("2 sheet copy/copies"));
 System.out.println("PASS: "+count+" H5.2 profile/geometry assertions; no Android device or printer."); }
}
