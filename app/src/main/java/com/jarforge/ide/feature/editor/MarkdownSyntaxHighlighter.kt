package com.jarforge.ide.feature.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration

enum class MarkdownTokenKind { HEADING, BOLD, ITALIC, STRIKE, CODE_INLINE, CODE_FENCE, CODE_BLOCK, LIST_MARKER, RULE, LINK_TEXT, LINK_URL, QUOTE }

/**
 * Not CommonMark-compliant; approximates common constructs.
 * Supports common block and single-line inline Markdown only. Setext headings, indented code,
 * GFM tables, HTML blocks/comments, reference links, bare URLs, task lists, front matter,
 * multiline inline constructs, nested inline parsing, container-aware fences/lists, entities,
 * footnotes, math and fenced-language tokenization are intentionally unsupported. Heading lines
 * are colored as a whole; emphasis precedence and edge cases are simplified.
 */
object MarkdownSyntaxHighlighter {
    const val MAX_HIGHLIGHT_LENGTH = 100_000
    private data class Fence(val char: Char, val length: Int)
    private val emphasisMarkers = listOf("***", "___", "**", "__", "~~", "*", "_")
    fun tokenize(text: CharSequence, emit: (Int, Int, MarkdownTokenKind) -> Unit) {
        if(text.isEmpty()||text.length>MAX_HIGHLIGHT_LENGTH)return
        var pos=0; var fence:Fence?=null
        while(pos<text.length) {
            val endLine=run { var e=pos;while(e<text.length&&text[e]!='\n'&&text[e]!='\r')e++;e }
            val line=text.subSequence(pos,endLine).toString()
            val closeOrOpen=fence
            if(closeOrOpen!=null) {
                val close=closingFence(line,closeOrOpen)
                if(close!=null) emit(pos+close.first,pos+close.second,MarkdownTokenKind.CODE_FENCE)
                else if(endLine>pos) emit(pos,endLine,MarkdownTokenKind.CODE_BLOCK)
                fence=if(close!=null)null else fence
            } else {
                val open=openingFence(line)
                if(open!=null) { emit(pos+open.first,pos+line.length,MarkdownTokenKind.CODE_FENCE);fence=open.third }
                else blockLine(text,pos,endLine,line,emit)
            }
            pos=when { endLine<text.length&&text[endLine]=='\r'&&endLine+1<text.length&&text[endLine+1]=='\n'->endLine+2;endLine<text.length->endLine+1;else->endLine }
        }
    }
    private fun openingFence(line:String):Triple<Int,Int,Fence>? {
        var i=0;while(i<line.length&&line[i]==' '&&i<4)i++
        if(i>3||i>=line.length||line[i]=='\t')return null
        val ch=line[i];if(ch!='`'&&ch!='~')return null
        var e=i;while(e<line.length&&line[e]==ch)e++
        if(e-i<3)return null
        if(ch=='`'&&line.substring(e).contains('`'))return null
        return Triple(i,e,Fence(ch,e-i))
    }
    private fun closingFence(line:String,f:Fence):Pair<Int,Int>? {
        var i=0;while(i<line.length&&line[i]==' '&&i<4)i++
        if(i>3||i>=line.length||line[i]=='\t'||line[i]!=f.char)return null
        var e=i;while(e<line.length&&line[e]==f.char)e++
        if(e-i<f.length)return null
        for(k in e until line.length)if(line[k]!=' '&&line[k]!='\t')return null
        return i to e
    }
    private fun blockLine(t:CharSequence,start:Int,end:Int,line:String,emit:(Int,Int,MarkdownTokenKind)->Unit) {
        if(line.isEmpty())return
        var p=0;while(p<line.length&&line[p]==' '&&p<4)p++
        if(p<=3&&p<line.length&&line[p]=='#') {var e=p;while(e<line.length&&line[e]=='#')e++;if(e-p<=6&&(e==line.length||line[e]==' '||line[e]=='\t')){emit(start,end,MarkdownTokenKind.HEADING);return}}
        val trimmed=line.trim().filterNot{it==' '||it=='\t'}
        if(trimmed.length>=3&&trimmed.all{it==trimmed[0]}&&trimmed[0] in "-*_" ){emit(start,end,MarkdownTokenKind.RULE);return}
        if(p<line.length&&line[p]=='>'){emit(start+p,start+p+1,MarkdownTokenKind.QUOTE);inline(t,start,end,emit);return}
        if(p<line.length&&(line[p]=='-'||line[p]=='*'||line[p]=='+' )&&p+1<line.length&&line[p+1].isWhitespace()){emit(start+p,start+p+1,MarkdownTokenKind.LIST_MARKER);inline(t,start,end,emit);return}
        var q=p;while(q<line.length&&line[q].isDigit())q++
        if(q>p&&q+1<line.length&&(line[q]=='.'||line[q]==')')&&line[q+1].isWhitespace()){emit(start+p,start+q+1,MarkdownTokenKind.LIST_MARKER);inline(t,start,end,emit);return}
        // Link reference definition [ref]: URL
        if(line.startsWith("[")&&line.indexOf("]:")>1){val mid=line.indexOf("]:");emit(start,start+mid+1,MarkdownTokenKind.LINK_TEXT);var u=mid+2;while(u<line.length&&line[u].isWhitespace())u++;if(u<line.length)emit(start+u,end,MarkdownTokenKind.LINK_URL);return}
        inline(t,start,end,emit)
    }
    private fun inline(t:CharSequence,start:Int,end:Int,emit:(Int,Int,MarkdownTokenKind)->Unit) {
        val size=end-start
        var hasBackslash=false; var hasSquareOpen=false; var hasSquareClose=false
        var hasParenOpen=false; var hasParenClose=false; var hasAngleOpen=false; var hasAngleClose=false
        for(offset in 0 until size) when(t[start+offset]) {
            '\\'->hasBackslash=true; '['->hasSquareOpen=true; ']'->hasSquareClose=true
            '('->hasParenOpen=true; ')'->hasParenClose=true; '<'->hasAngleOpen=true; '>'->hasAngleClose=true
        }
        val escapedAt=if(hasBackslash)BooleanArray(size)else null
        if(escapedAt!=null) {
            var backslashes=0
            for(offset in 0 until size) {
                val c=t[start+offset]
                if(c=='\\') backslashes++ else { escapedAt[offset]=backslashes%2==1; backslashes=0 }
            }
        }
        val hasSquare=hasSquareOpen&&hasSquareClose
        val hasParen=hasParenOpen&&hasParenClose
        val hasAngle=hasAngleOpen&&hasAngleClose
        val nextSquare=if(hasSquare)IntArray(size+1){-1}else null
        val nextParen=if(hasParen)IntArray(size+1){-1}else null
        val nextAngle=if(hasAngle)IntArray(size+1){-1}else null
        var square=-1; var paren=-1; var angle=-1
        if(nextSquare!=null||nextParen!=null||nextAngle!=null) for(offset in size-1 downTo 0) {
            val c=t[start+offset]
            if(escapedAt?.get(offset)!=true) when(c) { ']'->square=offset; ')'->paren=offset; '>'->angle=offset }
            if(nextSquare!=null)nextSquare[offset]=square
            if(nextParen!=null)nextParen[offset]=paren
            if(nextAngle!=null)nextAngle[offset]=angle
        }
        var i=start
        while(i<end) {
            val offset=i-start
            if(t[i]=='\\'&&i+1<end){i+=2;continue}
            if(t[i]=='`'&&escapedAt?.get(offset)!=true) {
                var e=i;while(e<end&&t[e]=='`')e++
                val marker=t.subSequence(i,e).toString()
                val close=indexOf(t,marker,e,end,escapedAt,start)
                if(close>=0){emit(i,close+marker.length,MarkdownTokenKind.CODE_INLINE);i=close+marker.length;continue}
            }
            if((t[i]=='!'&&i+1<end&&t[i+1]=='[')||t[i]=='[') {
                val open=if(t[i]=='!')i+1 else i
                val openOffset=open-start
                val midOffset=if(openOffset+1<size)nextSquare?.get(openOffset+1) ?: -1 else -1
                if(midOffset>=0) {
                    val mid=start+midOffset
                    if(mid+1<end&&t[mid+1]=='(') {
                        val urlStart=mid+2-start
                        val closeOffset=if(urlStart<size)nextParen?.get(urlStart) ?: -1 else -1
                        if(closeOffset>=0){
                            if(mid>open+1)emit(open+1,mid,MarkdownTokenKind.LINK_TEXT)
                            val close=start+closeOffset
                            if(mid+2<close)emit(mid+2,close,MarkdownTokenKind.LINK_URL)
                            i=close+1;continue
                        }
                    }
                }
            }
            val isHttps=t[i]=='<'&&i+1<end&&t.subSequence(i+1,minOf(end,i+9)).toString().startsWith("https://")
            val isHttp=t[i]=='<'&&i+1<end&&t.subSequence(i+1,minOf(end,i+8)).toString().startsWith("http://")
            if((isHttps||isHttp)&&escapedAt?.get(offset)!=true) {
                val closeOffset=if(offset+1<size)nextAngle?.get(offset+1) ?: -1 else -1
                if(closeOffset>=0){val close=start+closeOffset;emit(i,close+1,MarkdownTokenKind.LINK_URL);i=close+1;continue}
            }
            val marker=emphasisMarkers.firstOrNull{m->i+m.length<=end&&t.subSequence(i,i+m.length).toString()==m&&escapedAt?.get(offset)!=true&&hasEmphasisBoundary(t,i,end,m,true)}
            if(marker!=null){
                val close=indexOf(t,marker,i+marker.length,end,escapedAt,start)
                if(close>i+marker.length&&hasEmphasisBoundary(t,close,end,marker,false)){
                    val kind=when(marker.length){3->MarkdownTokenKind.BOLD;2->if(marker=="~~")MarkdownTokenKind.STRIKE else MarkdownTokenKind.BOLD;else->MarkdownTokenKind.ITALIC}
                    emit(i,close+marker.length,kind);i=close+marker.length;continue
                }
            }
            i++
        }
    }

    private fun hasEmphasisBoundary(t:CharSequence,p:Int,end:Int,m:String,opening:Boolean):Boolean {
        val before=if(p>0)t[p-1] else ' ';val after=if(p+m.length<end)t[p+m.length] else ' '
        if(m.startsWith("_")&&before.isLetterOrDigit()&&after.isLetterOrDigit())return false
        return if(opening) !after.isWhitespace() else !before.isWhitespace()
    }
    private fun indexOf(t:CharSequence,s:String,from:Int,end:Int,escapedAt:BooleanArray?,base:Int):Int {
        var i=from
        while(i+s.length<=end){
            if(escapedAt?.get(i-base)!=true&&t.subSequence(i,i+s.length).toString()==s)return i
            i++
        }
        return -1
    }
}

@OptIn(ExperimentalFoundationApi::class)
object MarkdownHighlightTransformation : OutputTransformation {
    private val styles = mapOf(
        MarkdownTokenKind.HEADING to SpanStyle(color=SyntaxColors.Type),
        MarkdownTokenKind.BOLD to SpanStyle(color=SyntaxColors.Bold),
        MarkdownTokenKind.ITALIC to SpanStyle(color=SyntaxColors.Italic),
        MarkdownTokenKind.STRIKE to SpanStyle(color=SyntaxColors.Comment,textDecoration=TextDecoration.LineThrough),
        MarkdownTokenKind.CODE_INLINE to SpanStyle(color=SyntaxColors.StringLiteral),
        MarkdownTokenKind.CODE_FENCE to SpanStyle(color=SyntaxColors.StringLiteral),
        MarkdownTokenKind.CODE_BLOCK to SpanStyle(color=SyntaxColors.StringLiteral),
        MarkdownTokenKind.LIST_MARKER to SpanStyle(color=SyntaxColors.Operator),
        MarkdownTokenKind.RULE to SpanStyle(color=SyntaxColors.Comment),
        MarkdownTokenKind.LINK_TEXT to SpanStyle(color=SyntaxColors.Link),
        MarkdownTokenKind.LINK_URL to SpanStyle(color=SyntaxColors.Quote),
        MarkdownTokenKind.QUOTE to SpanStyle(color=SyntaxColors.Quote)
    )
    override fun TextFieldBuffer.transformOutput(){if(length==0||length>MarkdownSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH)return;MarkdownSyntaxHighlighter.tokenize(asCharSequence()){s,e,k->addStyle(styles.getValue(k),s,e)}}
}
