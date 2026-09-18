package com.tomasthrawat.candycrush

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    companion object { const val N=8; const val TYPES=6 }
    enum class Screen { MENU, LEVELS, GAME, SHOP, HELP, SETTINGS }
    private val prefs=context.getSharedPreferences("sweet_match",0)
    private val board=Array(N){IntArray(N){-1}}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val tone=ToneGenerator(AudioManager.STREAM_MUSIC,70)
    private var screen=Screen.MENU
    private var level=1; private var score=0; private var moves=25; private var target=625
    private var coins=prefs.getInt("coins",0); private var hammer=prefs.getInt("hammer",2); private var shuffle=prefs.getInt("shuffle",1)
    private var sr=-1; private var sc=-1; private var downX=0f; private var downY=0f
    private var anim=0L; private var ar=-1; private var ac=-1; private var br=-1; private var bc=-1
    private var pendingResolve=false
    private var msg=""; private var msgUntil=0L
    private var fps=prefs.getString("fps","NO CAP") ?: "NO CAP"
    private var graphics=prefs.getString("graphics","HIGH") ?: "HIGH"
    private var shadows=prefs.getBoolean("shadows",true)
    private var sound=prefs.getBoolean("sound",true)
    private var look=prefs.getString("look","CLASSIC") ?: "CLASSIC"
    init { setLayerType(LAYER_TYPE_HARDWARE,null); isFocusable=true }
    override fun onDraw(c:Canvas){
        c.drawColor(0xFFFFF8F0.toInt())
        when(screen){Screen.MENU->menu(c);Screen.LEVELS->levels(c);Screen.GAME->game(c);Screen.SHOP->shop(c);Screen.HELP->help(c);Screen.SETTINGS->settings(c)}
        val now=System.currentTimeMillis()
        if(msgUntil>now){text(c,msg,width/2f,height*.96f,width*.038f,0xFF6B5265.toInt(),true);requestFrame()}
        if(screen==Screen.GAME&&anim>0L){
            val elapsed=now-anim
            if(elapsed<220L) requestFrame()
            else{
                if(pendingResolve){resolve();pendingResolve=false}
                anim=0L; ar=-1; ac=-1; br=-1; bc=-1
                invalidate()
            }
        }
    }
    private fun requestFrame(){when(fps){"60"->postDelayed({invalidate()},16);"90"->postDelayed({invalidate()},11);"120"->postDelayed({invalidate()},8);else->postInvalidateOnAnimation()}}
    private fun menu(c:Canvas){text(c,"CANDY FAMILY",width/2f,height*.15f,width*.08f,0xFF8B5E4A.toInt(),true);text(c,"Level: $level    Coins: $coins",width/2f,height*.22f,width*.038f,0xFF7A5600.toInt(),true);btn(c,.15f,.29f,.85f,.38f,"PLAY");btn(c,.15f,.41f,.85f,.50f,"LEVELS");btn(c,.15f,.53f,.85f,.62f,"SHOP");btn(c,.15f,.65f,.85f,.74f,"SETTINGS");btn(c,.15f,.77f,.85f,.86f,"HOW TO PLAY")}
    private var levelScroll=0f
    private fun levels(c:Canvas){
        text(c,"LEVELS",width/2f,height*.07f,width*.065f,0xFF8B5E4A.toInt(),true)
        text(c,"Scroll for unlimited levels",width/2f,height*.13f,width*.032f,0xFF6B5265.toInt(),false)
        val top=height*.17f
        val rowH=height*.095f
        val firstRow=(levelScroll/rowH).toInt().coerceAtLeast(0)
        val offset=-(levelScroll-firstRow*rowH)
        for(row in firstRow..firstRow+7){
            val y=top+offset+(row-firstRow)*rowH
            if(y>height*.87f||y+height*.07f<top) continue
            for(col in 0..3){
                val levelNo=row*4+col+1
                val l=width*.06f+col*width*.235f
                box(c,l,y,l+width*.19f,y+height*.065f,0xFFFFD6E8.toInt())
                text(c,levelNo.toString(),l+width*.095f,y+height*.044f,width*.034f,Color.WHITE,true)
            }
        }
        btn(c,.2f,.91f,.8f,.98f,"BACK")
    }
    private fun settings(c:Canvas){text(c,"SETTINGS",width/2f,height*.09f,width*.065f,0xFF8B5E4A.toInt(),true);text(c,"VIDEO / GRAPHICS",width/2f,height*.16f,width*.038f,0xFF6B5265.toInt(),true);btn(c,.12f,.20f,.88f,.28f,"FPS: $fps");btn(c,.12f,.31f,.88f,.39f,"GRAPHICS: $graphics");btn(c,.12f,.42f,.88f,.50f,"SHADOWS: "+if(shadows)"ON" else "OFF");box(c,width*.12f,height*.53f,width*.88f,height*.61f,0xFFFFD6E8.toInt())
        text(c,"SHAPE",width*.20f,height*.577f,width*.028f,0xFF6B5265.toInt(),true)
        drawTypeIcon(c,width*.72f,height*.57f,width*.11f,(look.toIntOrNull()?.minus(1)?.coerceIn(0,9) ?: 0))btn(c,.12f,.64f,.88f,.72f,"SOUND: "+if(sound)"ON" else "OFF");btn(c,.20f,.83f,.80f,.91f,"BACK")}
    private fun game(c:Canvas){text(c,"LEVEL $level",width*.16f,height*.06f,width*.043f,0xFF8B5E4A.toInt(),true);text(c,"$score/$target",width*.50f,height*.06f,width*.038f,0xFF6B5265.toInt(),true);text(c,"MOVES $moves",width*.83f,height*.06f,width*.038f,0xFF6B5265.toInt(),true);val top=height*.12f;val cell=min(width*.112f,height*.68f/N);val left=(width-cell*N)/2f;val rawProg=if(anim>0L)((System.currentTimeMillis()-anim)/220f).coerceIn(0f,1f) else 1f
        val prog=1f-(1f-rawProg)*(1f-rawProg)
        for(r in 0 until N)for(col in 0 until N){val x=left+col*cell;val y=top+r*cell;box(c,x+1,y+1,x+cell-1,y+cell-1,0x22FFFFFF.toInt());if(board[r][col]>=0){var dx=0f;var dy=0f;if(prog<1f&&r==ar&&col==ac){dx=(bc-ac)*cell*prog;dy=(br-ar)*cell*prog};if(prog<1f&&r==br&&col==bc){dx=(ac-bc)*cell*prog;dy=(ar-br)*cell*prog};if(shadows&&graphics!="LOW"){paint.color=0x33000000;c.drawCircle(x+cell/2+dx+3,y+cell/2+dy+4,cell*.35f,paint)};candy(c,x+cell/2+dx,y+cell/2+dy,cell*.35f,board[r][col])}};helperBtn(c,.04f,.83f,.30f,.92f,0,hammer);helperBtn(c,.35f,.83f,.65f,.92f,1,shuffle);btn(c,.70f,.83f,.96f,.92f,"LOBBY");if(score>=target||moves<=0){paint.color=0xEE24152F.toInt();c.drawRect(0f,height*.30f,width.toFloat(),height*.68f,paint);text(c,if(score>=target)"LEVEL COMPLETE" else "OUT OF MOVES",width/2f,height*.43f,width*.06f,Color.WHITE,true);text(c,if(score>=target)"+"+reward()+" COINS" else "TRY AGAIN",width/2f,height*.51f,width*.045f,Color.WHITE,true);btn(c,.2f,.57f,.8f,.65f,if(score>=target)"CONTINUE" else "RETRY")}}
    private fun shop(c:Canvas){text(c,"SHOP",width/2f,height*.09f,width*.065f,0xFF8B5E4A.toInt(),true);text(c,"Coins: $coins",width/2f,height*.15f,width*.038f,0xFF7A5600.toInt(),true);item(c,.18f,.22f,"HAMMER","Break a candy",hammer,30);item(c,.18f,.43f,"SHUFFLE","New board",shuffle,45);btn(c,.2f,.78f,.8f,.86f,"BACK")}
    private fun help(c:Canvas){text(c,"HOW TO PLAY",width/2f,height*.1f,width*.06f,0xFF8B5E4A.toInt(),true);listOf("Swipe adjacent candies to move them.","Swaps work even without a match.","Make 3+ matches for points.","Cascades give bonus points.","Reach the target before moves run out.","Levels are generated continuously.","Use helpers from the game screen.").forEachIndexed{i,s->text(c,s,width/2f,height*(.21f+i*.08f),width*.03f,0xFF6B5265.toInt(),false)};btn(c,.2f,.80f,.8f,.88f,"BACK")}
    private fun drawShopIcon(c:Canvas,x:Float,y:Float,n:String){
        when(n){
            "HAMMER" -> drawHammer(c,x,y)
            "SHUFFLE" -> drawShuffle(c,x,y)
            else -> drawCandyIcon(c,x,y)
        }
    }

    private fun drawCandyIcon(c:Canvas,x:Float,y:Float){
        paint.color=0xFFFF8FBE.toInt()
        c.drawCircle(x,y,width*.045f,paint)
        paint.color=0xFFFFFFFF.toInt()
        c.drawCircle(x-width*.014f,y-height*.012f,width*.010f,paint)
        c.drawCircle(x+width*.014f,y-height*.012f,width*.010f,paint)
        paint.color=0xFF7B3F98.toInt()
        paint.style=Paint.Style.STROKE
        paint.strokeWidth=width*.008f
        c.drawArc(x-width*.018f,y-height*.002f,x+width*.018f,y+height*.028f,10f,160f,false,paint)
        paint.style=Paint.Style.FILL
    }

    private fun item(c:Canvas,x:Float,y:Float,n:String,d:String,count:Int,price:Int){
        box(c,width*x,height*y,width*(1-x),height*(y+.15f),0xFFFFF7FB.toInt())
        drawShopIcon(c,width*.50f,height*(y+.075f),n)
        text(c,d,width*.50f,height*(y+.10f),width*.029f,0xFF6B5265.toInt(),false)
        text(c,"OWNED $count",width*.77f,height*(y+.05f),width*.026f,0xFFFF8FBE.toInt(),true)
        btn(c,.70f,y+.10f,.94f,y+.145f,"BUY $price")
    }
    private fun btn(c:Canvas,l:Float,t:Float,r:Float,bb:Float,s:String){
        val x1=width*l; val y1=height*t; val x2=width*r; val y2=height*bb
        box(c,x1,y1,x2,y2,0xFF8B5E4A.toInt())
        var size=height*(bb-t)*.40f
        paint.typeface=Typeface.create("sans",Typeface.BOLD)
        paint.textSize=size
        val maxWidth=(x2-x1)*.84f
        val measured=paint.measureText(s)
        if(measured>maxWidth) size*=maxWidth/measured
        text(c,s,(x1+x2)/2f,(y1+y2)/2f+size*.34f,size,Color.WHITE,true)
    }
    private fun box(c:Canvas,l:Float,t:Float,r:Float,bb:Float,color:Int){paint.style=Paint.Style.FILL;paint.color=color;c.drawRoundRect(l,t,r,bb,14f,14f,paint)}
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean){paint.color=color;paint.textSize=size;paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans",if(bold)Typeface.BOLD else Typeface.NORMAL);c.drawText(s,x,y,paint)}
private fun candy(c:Canvas,x:Float,y:Float,r:Float,v:Int){
        when(v%10){
            0->drawStrawberry(c,x,y,r)
            1->drawLemon(c,x,y,r)
            2->drawBlueberry(c,x,y,r)
            3->drawApple(c,x,y,r)
            4->drawFlower(c,x,y,r)
            5->drawCookie(c,x,y,r)
            6->drawWrappedCandy(c,x,y,r)
            7->drawGem(c,x,y,r)
            8->drawOrange(c,x,y,r)
            else->drawDonut(c,x,y,r)
        }
    }
    private fun finishShape(c:Canvas,x:Float,y:Float,r:Float,color:Int){
        paint.color=color
        paint.style=Paint.Style.STROKE
        paint.strokeWidth=r*.055f
        paint.strokeJoin=Paint.Join.ROUND
        paint.strokeCap=Paint.Cap.ROUND
        paint.color=0x552D2330.toInt()
        paint.style=Paint.Style.STROKE
        paint.strokeWidth=r*.055f
    }
    private fun shine(c:Canvas,x:Float,y:Float,r:Float){
        paint.style=Paint.Style.FILL
        paint.color=0x66FFFFFF
        c.drawOval(x-r*.48f,y-r*.56f,x-r*.14f,y-r*.22f,paint)
    }
    private fun drawStrawberry(c:Canvas,x:Float,y:Float,r:Float){
        paint.style=Paint.Style.FILL
        paint.color=0xFFE85D68.toInt()
        val p=Path()
        p.moveTo(x,y+r*.98f)
        p.cubicTo(x-r*1.05f,y+r*.20f,x-r*.72f,y-r*.62f,x,y-r*.30f)
        p.cubicTo(x+r*.72f,y-r*.62f,x+r*1.05f,y+r*.20f,x,y+r*.98f)
        p.close()
        c.drawPath(p,paint)
        paint.color=0xFF5C9E52.toInt()
        val leaf=Path()
        leaf.moveTo(x,y-r*.38f);leaf.lineTo(x-r*.52f,y-r*.76f);leaf.lineTo(x-r*.10f,y-r*.74f)
        leaf.lineTo(x,y-r*1.04f);leaf.lineTo(x+r*.10f,y-r*.74f);leaf.lineTo(x+r*.52f,y-r*.76f);leaf.close()
        c.drawPath(leaf,paint)
        paint.color=0xFFFFF0C7.toInt()
        for(i in -1..1)c.drawOval(x+i*r*.28f,y-r*.02f,x+i*r*.28f+r*.055f,y+r*.15f,paint)
        shine(c,x-r*.02f,y+r*.02f,r)
    }
    private fun drawLemon(c:Canvas,x:Float,y:Float,r:Float){
        paint.style=Paint.Style.FILL
        paint.color=0xFFF2C14E.toInt()
        c.drawOval(x-r*.92f,y-r*.70f,x+r*.92f,y+r*.70f,paint)
        paint.color=0xFFFFE58A.toInt()
        c.drawOval(x-r*.70f,y-r*.50f,x+r*.15f,y+r*.38f,paint)
        paint.color=0xFF7AAE62.toInt()
        c.drawOval(x+r*.35f,y-r*.70f,x+r*.72f,y-r*.45f,paint)
    }
    private fun drawBlueberry(c:Canvas,x:Float,y:Float,r:Float){
        paint.style=Paint.Style.FILL
        paint.color=0xFF5B8DEF.toInt()
        c.drawCircle(x,y,r*.88f,paint)
        paint.color=0xFF6B4E9B.toInt()
        val p=Path()
        p.moveTo(x-r*.42f,y-r*.66f);p.lineTo(x,y-r*.92f);p.lineTo(x+r*.42f,y-r*.66f)
        p.lineTo(x+r*.12f,y-r*.40f);p.lineTo(x-r*.12f,y-r*.40f);p.close()
        c.drawPath(p,paint)
        shine(c,x+r*.05f,y+r*.02f,r)
    }
    private fun drawApple(c:Canvas,x:Float,y:Float,r:Float){
        paint.style=Paint.Style.FILL
        paint.color=0xFF70B77A.toInt()
        val p=Path()
        p.moveTo(x,y+r*.90f)
        p.cubicTo(x-r*1.04f,y+r*.35f,x-r*.78f,y-r*.68f,x,y-r*.34f)
        p.cubicTo(x+r*.78f,y-r*.68f,x+r*1.04f,y+r*.35f,x,y+r*.90f)
        p.close();c.drawPath(p,paint)
        paint.color=0xFF5C9E52.toInt();c.drawOval(x+r*.05f,y-r*.84f,x+r*.58f,y-r*.43f,paint)
        paint.color=0xFF6D4938.toInt();c.drawRoundRect(x-r*.06f,y-r*.96f,x+r*.06f,y-r*.62f,r*.04f,r*.04f,paint)
        shine(c,x,y+r*.03f,r)
    }
    private fun drawFlower(c:Canvas,x:Float,y:Float,r:Float){
        paint.style=Paint.Style.FILL
        paint.color=0xFFD889C8.toInt()
        for(i in 0..5){
            val a=i*Math.PI/3
            val px=x+(kotlin.math.cos(a)*r*.56f).toFloat()
            val py=y+(kotlin.math.sin(a)*r*.56f).toFloat()
            c.drawCircle(px,py,r*.47f,paint)
        }
        paint.color=0xFFF6D365.toInt();c.drawCircle(x,y,r*.38f,paint)
        paint.color=0xFFFFB6DE
        c.drawCircle(x-r*.18f,y-r*.12f,r*.08f,paint)
    }
    private fun drawCookie(c:Canvas,x:Float,y:Float,r:Float){
        paint.style=Paint.Style.FILL
        paint.color=0xFFB97850.toInt();c.drawCircle(x,y,r*.86f,paint)
        paint.color=0xFF704A36.toInt()
        for(i in 0..5){
            val a=i*Math.PI/3
            c.drawCircle(x+(kotlin.math.cos(a)*r*.45f).toFloat(),y+(kotlin.math.sin(a)*r*.45f).toFloat(),r*.09f,paint)
        }
        c.drawCircle(x+r*.10f,y-r*.18f,r*.07f,paint)
        shine(c,x+r*.05f,y+r*.05f,r)
    }
    private fun drawWrappedCandy(c:Canvas,x:Float,y:Float,r:Float){
        paint.style=Paint.Style.FILL
        paint.color=0xFF62C5C9.toInt()
        c.drawRoundRect(x-r*.56f,y-r*.48f,x+r*.56f,y+r*.48f,r*.18f,r*.18f,paint)
        val p=Path()
        p.moveTo(x-r*.54f,y-r*.34f);p.lineTo(x-r*1.02f,y-r*.70f);p.lineTo(x-r*.90f,y);p.lineTo(x-r*1.02f,y+r*.70f);p.lineTo(x-r*.54f,y+r*.34f);p.close();c.drawPath(p,paint)
        val q=Path()
        q.moveTo(x+r*.54f,y-r*.34f);q.lineTo(x+r*1.02f,y-r*.70f);q.lineTo(x+r*.90f,y);q.lineTo(x+r*1.02f,y+r*.70f);q.lineTo(x+r*.54f,y+r*.34f);q.close();c.drawPath(q,paint)
        paint.color=0xFF3D9FA5.toInt()
        c.drawRoundRect(x-r*.12f,y-r*.48f,x+r*.12f,y+r*.48f,r*.06f,r*.06f,paint)
        shine(c,x+r*.02f,y+r*.02f,r)
    }
    private fun drawGem(c:Canvas,x:Float,y:Float,r:Float){
        paint.style=Paint.Style.FILL
        paint.color=0xFF9A72D6.toInt()
        val p=Path()
        p.moveTo(x,y-r);p.lineTo(x+r*.82f,y-r*.34f);p.lineTo(x+r*.58f,y+r*.82f);p.lineTo(x-r*.58f,y+r*.82f);p.lineTo(x-r*.82f,y-r*.34f);p.close()
        c.drawPath(p,paint)
        paint.color=0xFFBDA3F0.toInt()
        val h=Path();h.moveTo(x-r*.52f,y-r*.28f);h.lineTo(x,y-r*.72f);h.lineTo(x+r*.10f,y-r*.28f);h.close();c.drawPath(h,paint)
    }
    private fun drawOrange(c:Canvas,x:Float,y:Float,r:Float){
        paint.style=Paint.Style.FILL
        paint.color=0xFFFF8C42.toInt();c.drawCircle(x,y,r*.86f,paint)
        paint.color=0xFFFFC266.toInt();c.drawArc(x-r*.55f,y-r*.55f,x+r*.55f,y+r*.55f,-70f,70f,false,paint)
        paint.color=0xFF5C9E52.toInt();c.drawOval(x-r*.08f,y-r*.95f,x+r*.42f,y-r*.55f,paint)
        shine(c,x,y,r)
    }
    private fun drawDonut(c:Canvas,x:Float,y:Float,r:Float){
        paint.style=Paint.Style.FILL
        paint.color=0xFFE6A15A.toInt();c.drawCircle(x,y,r*.86f,paint)
        paint.color=0xFFFFC2A1.toInt();c.drawCircle(x,y,r*.49f,paint)
        paint.color=0xFF6D4938.toInt();c.drawCircle(x,y,r*.20f,paint)
        paint.color=0xFFFFE0B8.toInt()
        for(i in 0..5){
            val a=i*Math.PI/3
            c.drawCircle(x+(kotlin.math.cos(a)*r*.58f).toFloat(),y+(kotlin.math.sin(a)*r*.58f).toFloat(),r*.055f,paint)
        }
        shine(c,x,y,r)
    }
    private fun helperBtn(c:Canvas,l:Float,t:Float,r:Float,bb:Float,type:Int,count:Int){    private fun helperBtn(c:Canvas,l:Float,t:Float,r:Float,bb:Float,type:Int,count:Int){
        val x1=width*l;val y1=height*t;val x2=width*r;val y2=height*bb
        box(c,x1,y1,x2,y2,0xFF8B5E4A.toInt())
        val cx=(x1+x2)/2f;val cy=(y1+y2)/2f
        if(type==0)drawHammer(c,cx-10f,cy)
        else drawShuffle(c,cx-12f,cy)
        text(c,count.toString(),cx+width*.055f,cy+height*.014f,width*.035f,Color.WHITE,true)
    }
    private fun drawHammer(c:Canvas,x:Float,y:Float){
        paint.color=0xFFE7B35A.toInt()
        paint.strokeWidth=width*.018f
        paint.strokeCap=Paint.Cap.ROUND
        c.drawLine(x-2f,y+height*.025f,x+width*.035f,y-height*.025f,paint)
        paint.color=0xFFB85C4A.toInt()
        c.drawRoundRect(x-width*.065f,y-height*.045f,x+width*.025f,y-height*.005f,8f,8f,paint)
        paint.strokeCap=Paint.Cap.BUTT
    }
    private fun drawShuffle(c:Canvas,x:Float,y:Float){
        paint.color=Color.WHITE
        paint.style=Paint.Style.STROKE
        paint.strokeWidth=width*.012f
        val p1=Path();p1.moveTo(x-width*.055f,y-height*.025f);p1.cubicTo(x-width*.01f,y-height*.025f,x-width*.005f,y+height*.025f,x+width*.045f,y+height*.025f);c.drawPath(p1,paint)
        val p2=Path();p2.moveTo(x-width*.055f,y+height*.025f);p2.cubicTo(x-width*.01f,y+height*.025f,x-width*.005f,y-height*.025f,x+width*.045f,y-height*.025f);c.drawPath(p2,paint)
        paint.style=Paint.Style.FILL
        val q=Path();q.moveTo(x+width*.045f,y-height*.025f);q.lineTo(x+width*.025f,y-height*.040f);q.lineTo(x+width*.028f,y-height*.010f);q.close();c.drawPath(q,paint)
        val q2=Path();q2.moveTo(x+width*.045f,y+height*.025f);q2.lineTo(x+width*.025f,y+height*.040f);q2.lineTo(x+width*.028f,y+height*.010f);q2.close();c.drawPath(q2,paint)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{
                downX=e.x; downY=e.y
                if(screen==Screen.GAME){
                    val top=height*.12f
                    val cell=min(width*.112f,height*.68f/N)
                    val left=(width-cell*N)/2f
                    val col=((e.x-left)/cell).toInt()
                    val row=((e.y-top)/cell).toInt()
                    if(row in 0 until N && col in 0 until N){sr=row;sc=col}
                }
                return true
            }
            MotionEvent.ACTION_UP->{tap(e.x,e.y);return true}
            MotionEvent.ACTION_CANCEL->{sr=-1;sc=-1;return true}
        }
        return true
    }
    private fun tap(x:Float,y:Float){when(screen){Screen.MENU->when{y in height*.29f..height*.38f->start(level);y in height*.41f..height*.50f->screen=Screen.LEVELS;y in height*.53f..height*.62f->screen=Screen.SHOP;y in height*.65f..height*.74f->screen=Screen.SETTINGS;y in height*.77f..height*.86f->screen=Screen.HELP};Screen.LEVELS->{
                val dy=y-downY
                if(abs(dy)>18f){
                    levelScroll=(levelScroll-dy).coerceAtLeast(0f)
                }else if(y>height*.89f){
                    screen=Screen.MENU
                }else{
                    val row=((y-height*.17f+levelScroll)/(height*.095f)).toInt()
                    val col=((x-width*.06f)/(width*.235f)).toInt()
                    if(col in 0..3 && row>=0) start(row*4+col+1)
                }
            };Screen.SETTINGS->settingsTap(y);Screen.SHOP->{when{y in height*.22f..height*.37f->buyH();y in height*.43f..height*.58f->buyS();y>height*.78f->screen=Screen.MENU}};Screen.HELP->if(y>height*.78f)screen=Screen.MENU;Screen.GAME->gameTap(x,y)};if(sound)tone.startTone(ToneGenerator.TONE_PROP_BEEP,35);invalidate()}
    private fun drawTypeIcon(c:Canvas,x:Float,y:Float,r:Float,type:Int){
        candy(c,x,y,r*.72f,type)
    }
    private fun settingsTap(y:Float){when{y in height*.20f..height*.28f->fps=when(fps){"60"->"90";"90"->"120";"120"->"NO CAP";else->"60"};y in height*.31f..height*.39f->graphics=when(graphics){"LOW"->"MEDIUM";"MEDIUM"->"HIGH";else->"LOW"};y in height*.42f..height*.50f->shadows=!shadows;y in height*.53f..height*.61f->look=((look.toIntOrNull() ?: 0)%10+1).toString();y in height*.64f..height*.72f->sound=!sound;y>height*.83f->screen=Screen.MENU};save()}
    private fun gameTap(x:Float,y:Float){
        if(score>=target||moves<=0){
            if(y in height*.57f..height*.65f){if(score>=target)complete()else start(level)}
            else if(y>height*.83f && x>=width*.70f) screen=Screen.MENU
            return
        }
        if(y>height*.83f){
            when{x<width*.32f->useH();x<width*.68f->useS();else->screen=Screen.MENU}
            return
        }
        val top=height*.12f
        val cell=min(width*.112f,height*.68f/N)
        val left=(width-cell*N)/2f
        val col=((x-left)/cell).toInt()
        val row=((y-top)/cell).toInt()
        if(anim>0L)return
        if(row !in 0 until N||col !in 0 until N){sr=-1;sc=-1;return}
        if(sr !in 0 until N||sc !in 0 until N){sr=row;sc=col;return}
        val dx=x-downX
        val dy=y-downY
        if(abs(dx)<32&&abs(dy)<32){sr=-1;sc=-1;return}
        var tr=sr; var tc=sc
        if(abs(dx)>abs(dy)) tc+=if(dx>0)1 else -1 else tr+=if(dy>0)1 else -1
        if(tr in 0 until N&&tc in 0 until N) move(sr,sc,tr,tc)
        sr=-1;sc=-1
    }
    private fun start(l:Int){level=l.coerceAtLeast(1);score=0;moves=25+level/4;target=400+level*225;fill();screen=Screen.GAME}
    private fun fill(){for(r in 0 until N)for(c in 0 until N){do{board[r][c]=Random.nextInt(TYPES)}while(matchAt(r,c))}}
    private fun matchAt(r:Int,c:Int)=c>=2&&board[r][c]==board[r][c-1]&&board[r][c]==board[r][c-2]||r>=2&&board[r][c]==board[r-1][c]&&board[r][c]==board[r-2][c]
    private fun move(r1:Int,c1:Int,r2:Int,c2:Int){
        if(anim>0L)return
        swap(r1,c1,r2,c2)
        moves--
        ar=r1;ac=c1;br=r2;bc=c2
        pendingResolve=matches().isNotEmpty()
        anim=System.currentTimeMillis()
        requestFrame()
    }
    private fun swap(r1:Int,c1:Int,r2:Int,c2:Int){val t=board[r1][c1];board[r1][c1]=board[r2][c2];board[r2][c2]=t}
    private fun matches():Set<Pair<Int,Int>>{val o=mutableSetOf<Pair<Int,Int>>();for(r in 0 until N){var s=0;while(s<N){var e=s+1;while(e<N&&board[r][e]>=0&&board[r][e]==board[r][s])e++;if(board[r][s]>=0&&e-s>=3)for(c in s until e)o.add(r to c);s=e}};for(c in 0 until N){var s=0;while(s<N){var e=s+1;while(e<N&&board[e][c]>=0&&board[e][c]==board[s][c])e++;if(board[s][c]>=0&&e-s>=3)for(r in s until e)o.add(r to c);s=e}};return o}
    private fun resolve(){var combo=0;while(true){val m=matches();if(m.isEmpty())break;combo++;score+=m.size*25*combo;m.forEach{board[it.first][it.second]=-1};for(c in 0 until N){var w=N-1;for(r in N-1 downTo 0)if(board[r][c]>=0){board[w][c]=board[r][c];if(w!=r)board[r][c]=-1;w--};while(w>=0){board[w][c]=Random.nextInt(TYPES);w--}}}}
    private fun reward()=20+level*5
    private fun complete(){coins+=reward();prefs.edit().putInt("coins",coins).apply();screen=Screen.LEVELS}
    private fun useH(){if(hammer<=0){msg("BUY A HAMMER");return};val top=height*.12f;val cell=min(width*.112f,height*.68f/N);val left=(width-cell*N)/2;val col=((downX-left)/cell).toInt().coerceIn(0,N-1);val row=((downY-top)/cell).toInt().coerceIn(0,N-1);board[row][col]=Random.nextInt(TYPES);hammer--;save();resolve()}
    private fun useS(){if(shuffle<=0){msg("BUY A SHUFFLE");return};fill();shuffle--;save()}
    private fun buyH(){if(coins<30)msg("NEED 30 COINS")else{coins-=30;hammer++;save()}}
    private fun buyS(){if(coins<45)msg("NEED 45 COINS")else{coins-=45;shuffle++;save()}}
    private fun save(){prefs.edit().putInt("coins",coins).putInt("hammer",hammer).putInt("shuffle",shuffle).apply()}
    private fun msg(s:String){msg=s;msgUntil=System.currentTimeMillis()+1200}
}