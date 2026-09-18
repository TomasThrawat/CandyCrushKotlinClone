package com.tomasthrawat.candycrush

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    companion object { const val N=8; const val TYPES=6; const val MAX_LEVEL=20 }
    enum class Screen { MENU, LEVELS, GAME, SHOP, HELP }
    private val p=context.getSharedPreferences("sweet_match",0)
    private val b=Array(N){IntArray(N){-1}}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors=intArrayOf(0xFFE84A5F.toInt(),0xFFFFB84D.toInt(),0xFF57C7FF.toInt(),0xFF65D66E.toInt(),0xFFB477FF.toInt(),0xFFFF70B7.toInt())
    private var screen=Screen.MENU; private var level=1; private var score=0; private var moves=25; private var target=625
    private var coins=p.getInt("coins",0); private var hammer=p.getInt("hammer",2); private var shuffle=p.getInt("shuffle",1)
    private var sr=-1; private var sc=-1; private var downX=0f; private var downY=0f
    private var msg=""; private var msgUntil=0L; private var anim=0L; private var ar=-1; private var ac=-1; private var br=-1; private var bc=-1
    init { setLayerType(LAYER_TYPE_HARDWARE,null); isFocusable=true }

    override fun onDraw(c:Canvas){ c.drawColor(0xFFFFF4EB.toInt()); when(screen){
        Screen.MENU->menu(c); Screen.LEVELS->levels(c); Screen.SHOP->shop(c); Screen.HELP->help(c); Screen.GAME->game(c)
    }; if(msgUntil>System.currentTimeMillis()){ text(c,msg,width/2f,height*.95f,width*.04f,0xFF4D4147.toInt(),true); postInvalidateOnAnimation() } }

    private fun menu(c:Canvas){
        text(c,"SWEET MATCH",width/2f,height*.17f,width*.085f,0xFF7B3F98.toInt(),true)
        text(c,"Match • Blast • Win",width/2f,height*.24f,width*.04f,0xFF704B5A.toInt(),true)
        btn(c,.15f,.33f,.85f,.43f,"PLAY"); btn(c,.15f,.47f,.85f,.57f,"LEVELS"); btn(c,.15f,.61f,.85f,.71f,"SHOP"); btn(c,.15f,.75f,.85f,.85f,"HELP")
        text(c,"COINS: $coins$",width/2f,height*.91f,width*.04f,0xFF7A5600.toInt(),true)
    }
    private fun levels(c:Canvas){
        text(c,"LEVELS",width/2f,height*.09f,width*.07f,0xFF7B3F98.toInt(),true)
        val unlocked=p.getInt("unlocked",1)
        for(i in 1..MAX_LEVEL){ val col=(i-1)%4; val row=(i-1)/4; val l=width*.07f+col*width*.235f; val t=height*.17f+row*height*.12f
            box(c,l,t,l+width*.19f,t+height*.085f,if(i<=unlocked)0xFF8ED081.toInt() else 0xFFBDBDBD.toInt())
            text(c,if(i<=unlocked)i.toString() else "LOCKED",l+width*.095f,t+height*.055f,width*.027f,Color.WHITE,true)
        }; btn(c,.2f,.90f,.8f,.97f,"BACK")
    }
    private fun shop(c:Canvas){
        text(c,"SHOP",width/2f,height*.09f,width*.07f,0xFF7B3F98.toInt(),true)
        text(c,"Coins: $coins$",width/2f,height*.15f,width*.04f,0xFF7A5600.toInt(),true)
        item(c,.18f,.22f,"HAMMER","Break a candy",hammer,30); item(c,.18f,.43f,"SHUFFLE","New board",shuffle,45); btn(c,.2f,.78f,.8f,.86f,"BACK")
    }
    private fun help(c:Canvas){
        text(c,"HOW TO PLAY",width/2f,height*.1f,width*.065f,0xFF7B3F98.toInt(),true)
        listOf("Swipe adjacent candies to move them.","Make 3+ matches horizontally or vertically.","Cascades give bonus points.","Reach the target before moves run out.","Win levels to earn coins and unlock levels.","Use Hammer or Shuffle during a level.").forEachIndexed{i,s->text(c,s,width/2f,height*(.21f+i*.085f),width*.032f,0xFF4D4147.toInt(),false)}
        btn(c,.2f,.80f,.8f,.88f,"BACK")
    }
    private fun game(c:Canvas){
        text(c,"LEVEL $level$",width*.17f,height*.065f,width*.045f,0xFF7B3F98.toInt(),true)
        text(c,"$score$ / $target$",width*.51f,height*.065f,width*.04f,0xFF4D4147.toInt(),true)
        text(c,"MOVES $moves$",width*.84f,height*.065f,width*.04f,0xFF4D4147.toInt(),true)
        val top=height*.12f; val cell=min(width*.112f,height*.68f/N); val left=(width-cell*N)/2
        val progress=((System.currentTimeMillis()-anim)/140f).coerceIn(0f,1f)
        for(r in 0 until N)for(col in 0 until N){
            val x=left+col*cell; val y=top+r*cell; box(c,x+1,y+1,x+cell-1,y+cell-1,0x22FFFFFF)
            if(b[r][col]>=0){ var dx=0f;var dy=0f
                if(progress<1f&&r==ar&&col==ac){dx=(bc-ac)*cell*progress;dy=(br-ar)*cell*progress}
                if(progress<1f&&r==br&&col==bc){dx=(ac-bc)*cell*progress;dy=(ar-br)*cell*progress}
                candy(c,x+cell/2+dx,y+cell/2+dy,cell*.35f,colors[b[r][col]])
            }
            if(r==sr&&col==sc){paint.style=Paint.Style.STROKE;paint.strokeWidth=cell*.05f;paint.color=Color.WHITE;c.drawRoundRect(x+3,y+3,x+cell-3,y+cell-3,12f,12f,paint);paint.style=Paint.Style.FILL}
        }
        btn(c,.04f,.83f,.30f,.92f,"HAMMER $hammer$"); btn(c,.35f,.83f,.65f,.92f,"SHUFFLE $shuffle$"); btn(c,.70f,.83f,.96f,.92f,"MENU")
        if(score>=target||moves<=0){paint.color=0xEE24152F.toInt();c.drawRect(0f,height*.30f,width,height*.68f,paint);text(c,if(score>=target)"LEVEL COMPLETE" else "OUT OF MOVES",width/2f,height*.43f,width*.065f,Color.WHITE,true);text(c,if(score>=target)"+$reward()$ COINS" else "TRY AGAIN",width/2f,height*.51f,width*.05f,Color.WHITE,true);btn(c,.2f,.57f,.8f,.65f,if(score>=target)"CONTINUE" else "RETRY")}
        if(progress<1f)postInvalidateOnAnimation()
    }
    private fun item(c:Canvas,x:Float,y:Float,n:String,d:String,count:Int,price:Int){box(c,width*x,height*y,width*(1-x),height*(y+.15f),0xFFFFFBF7.toInt());text(c,n,width*.31f,height*(y+.05f),width*.038f,0xFF5C3B63.toInt(),true);text(c,d,width*.50f,height*(y+.10f),width*.03f,0xFF6D6268.toInt(),false);text(c,"OWNED $count$",width*.77f,height*(y+.05f),width*.027f,0xFF4D4147.toInt(),true);btn(c,.70f,y+.10f,.94f,y+.145f,"BUY $price$")}
    private fun btn(c:Canvas,l:Float,t:Float,r:Float,bb:Float,s:String){box(c,width*l,height*t,width*r,height*bb,0xFF7B3F98.toInt());text(c,s,width*(l+r)/2f,height*(t+bb)/2f+(height*(bb-t))*.16f,height*(bb-t)*.40f,Color.WHITE,true)}
    private fun box(c:Canvas,l:Float,t:Float,r:Float,bb:Float,color:Int){paint.style=Paint.Style.FILL;paint.color=color;c.drawRoundRect(l,t,r,bb,14f,14f,paint)}
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean){paint.color=color;paint.textSize=size;paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans",if(bold)Typeface.BOLD else Typeface.NORMAL);c.drawText(s.replace("$","$"),x,y,paint)}
    private fun candy(c:Canvas,x:Float,y:Float,r:Float,color:Int){paint.color=color;c.drawCircle(x,y,r,paint);paint.color=0x44FFFFFF;c.drawCircle(x-r*.3f,y-r*.3f,r*.23f,paint)}

    override fun onTouchEvent(e:MotionEvent):Boolean{when(e.actionMasked){MotionEvent.ACTION_DOWN->{downX=e.x;downY=e.y;return true};MotionEvent.ACTION_UP->{tap(e.x,e.y);return true}};return true}
    private fun tap(x:Float,y:Float){
        when(screen){
            Screen.MENU->when{y in height*.33f..height*.43f->start(p.getInt("unlocked",1));y in height*.47f..height*.57f->screen=Screen.LEVELS;y in height*.61f..height*.71f->screen=Screen.SHOP;y in height*.75f..height*.85f->screen=Screen.HELP}
            Screen.LEVELS->{if(y>height*.89f)screen=Screen.MENU else {val col=((x-width*.07f)/(width*.235f)).toInt();val row=((y-height*.17f)/(height*.12f)).toInt();val l=row*4+col+1;if(l in 1..MAX_LEVEL&&l<=p.getInt("unlocked",1))start(l)}}
            Screen.SHOP->{when{y in height*.22f..height*.37f->buyH();y in height*.43f..height*.58f->buyS();y>height*.78f->screen=Screen.MENU}}
            Screen.HELP->if(y>height*.78f)screen=Screen.MENU
            Screen.GAME->gameTap(x,y)
        };invalidate()
    }
    private fun gameTap(x:Float,y:Float){
        if(score>=target||moves<=0){if(y in height*.57f..height*.65f){if(score>=target)complete()else start(level)};return}
        if(y>height*.83f){when{ x<width*.32f->useH();x<width*.68f->useS();else->screen=Screen.MENU};return}
        val top=height*.12f;val cell=min(width*.112f,height*.68f/N);val left=(width-cell*N)/2;val col=((x-left)/cell).toInt();val row=((y-top)/cell).toInt()
        if(row !in 0 until N||col !in 0 until N)return
        val dx=x-downX;val dy=y-downY
        if(abs(dx)<32&&abs(dy)<32){sr=row;sc=col;return}
        if(sr<0){sr=row;sc=col;return}
        var tr=sr;var tc=sc;if(abs(dx)>abs(dy))tc+=if(dx>0)1 else -1 else tr+=if(dy>0)1 else -1
        if(tr in 0 until N&&tc in 0 until N)move(sr,sc,tr,tc);sr=-1;sc=-1
    }
    private fun start(l:Int){level=l;score=0;moves=25+level/4;target=400+level*225;fill();screen=Screen.GAME}
    private fun fill(){for(r in 0 until N)for(c in 0 until N){do{b[r][c]=Random.nextInt(TYPES)}while(matchAt(r,c))}}
    private fun matchAt(r:Int,c:Int)=c>=2&&b[r][c]==b[r][c-1]&&b[r][c]==b[r][c-2]||r>=2&&b[r][c]==b[r-1][c]&&b[r][c]==b[r-2][c]
    private fun move(r1:Int,c1:Int,r2:Int,c2:Int){swap(r1,c1,r2,c2);if(matches().isEmpty()){swap(r1,c1,r2,c2);msg("NO MATCH");return};moves--;ar=r1;ac=c1;br=r2;bc=c2;anim=System.currentTimeMillis();resolve();postInvalidateOnAnimation()}
    private fun swap(r1:Int,c1:Int,r2:Int,c2:Int){val t=b[r1][c1];b[r1][c1]=b[r2][c2];b[r2][c2]=t}
    private fun matches():Set<Pair<Int,Int>>{val out=mutableSetOf<Pair<Int,Int>>();for(r in 0 until N){var s=0;while(s<N){var e=s+1;while(e<N&&b[r][e]>=0&&b[r][e]==b[r][s])e++;if(b[r][s]>=0&&e-s>=3)for(c in s until e)out.add(r to c);s=e}};for(c in 0 until N){var s=0;while(s<N){var e=s+1;while(e<N&&b[e][c]>=0&&b[e][c]==b[s][c])e++;if(b[s][c]>=0&&e-s>=3)for(r in s until e)out.add(r to c);s=e}};return out}
    private fun resolve(){var combo=0;while(true){val m=matches();if(m.isEmpty())break;combo++;score+=m.size*25*combo;m.forEach{b[it.first][it.second]=-1};for(c in 0 until N){var w=N-1;for(r in N-1 downTo 0)if(b[r][c]>=0){b[w][c]=b[r][c];if(w!=r)b[r][c]=-1;w--};while(w>=0){b[w][c]=Random.nextInt(TYPES);w--}}}}
    private fun reward()=20+level*5
    private fun complete(){coins+=reward();if(level>=p.getInt("unlocked",1)&&level<MAX_LEVEL)p.edit().putInt("unlocked",level+1).apply();p.edit().putInt("coins",coins).apply();screen=Screen.LEVELS}
    private fun useH(){if(hammer<=0){msg("BUY A HAMMER");return};val top=height*.12f;val cell=min(width*.112f,height*.68f/N);val left=(width-cell*N)/2;val col=((downX-left)/cell).toInt().coerceIn(0,N-1);val row=((downY-top)/cell).toInt().coerceIn(0,N-1);b[row][col]=Random.nextInt(TYPES);hammer--;save();resolve()}
    private fun useS(){if(shuffle<=0){msg("BUY A SHUFFLE");return};fill();shuffle--;save()}
    private fun buyH(){if(coins<30)msg("NEED 30 COINS")else{coins-=30;hammer++;save()}}
    private fun buyS(){if(coins<45)msg("NEED 45 COINS")else{coins-=45;shuffle++;save()}}
    private fun save(){p.edit().putInt("coins",coins).putInt("hammer",hammer).putInt("shuffle",shuffle).apply()}
    private fun msg(s:String){msg=s;msgUntil=System.currentTimeMillis()+1200}
}