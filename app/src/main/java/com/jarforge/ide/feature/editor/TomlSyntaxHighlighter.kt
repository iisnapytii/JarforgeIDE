package com.jarforge.ide.feature.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.SpanStyle

enum class TomlTokenKind { TABLE, KEY, STRING, NUMBER, BOOLEAN, DATETIME, COMMENT, OPERATOR }

/** Linear TOML 1.0 highlighting lexer, not a validator. Incomplete constructs are tolerated. */
object TomlSyntaxHighlighter {
    const val MAX_HIGHLIGHT_LENGTH = 100_000
    fun tokenize(text: CharSequence, emit: (Int, Int, TomlTokenKind) -> Unit) {
        val n = text.length
        if (n == 0 || n > MAX_HIGHLIGHT_LENGTH) return
        val stack = ArrayList<Char>(8)
        var overflowDepth = 0
        var i = 0; var lineStart = true; var keyExpected = false
        while (i < n) {
            val c = text[i]
            if (c == '\n' || c == '\r') { if (c == '\n') { lineStart = true; if(overflowDepth==0 && stack.lastOrNull()=='{') stack.removeAt(stack.lastIndex) }; i++; continue }
            if (c == ' ' || c == '\t') { i++; continue }
            if (c == '#') { var e=i+1; while(e<n && text[e]!='\n' && text[e]!='\r') e++; emit(i,e,TomlTokenKind.COMMENT); i=e; continue }
            if (lineStart) {
                var p=i; while(p<n && (text[p]==' ' || text[p]=='\t')) p++
                if (p<n && text[p]=='[') {
                    val close = if (p+1<n && text[p+1]=='[') 2 else 1
                    var e=p+close
                    while(e<n && text[e]!='\n' && text[e]!='\r' && text[e]!='#') e++
                    if (e>p+close-1 && text[e-1]==']') { emit(p,e,TomlTokenKind.TABLE); i=e; lineStart=false; keyExpected=true; continue }
                }
                lineStart=false
                if (stack.lastOrNull()=='[' && looksAssignment(text,p)) stack.clear()
            }
            when {
                c=='"' || c=='\'' -> { val e=scanString(text,i,c); var q=e; while(q<n&&(text[q]==' '||text[q]=='\t'))q++; val key=q<n&&(text[q]=='='||text[q]=='.'); emit(i,e,if(key)TomlTokenKind.KEY else TomlTokenKind.STRING); i=e; keyExpected=false }
                c=='=' -> { emit(i,i+1,TomlTokenKind.OPERATOR); i++; keyExpected=false }
                c=='[' || c=='{' -> {
                    if(stack.size<32 && overflowDepth==0) stack.add(c) else overflowDepth++
                    if(c=='{') keyExpected=true
                    i++
                }
                c==']' || c=='}' -> {
                    val want=if(c==']') '[' else '{'
                    if(overflowDepth>0) overflowDepth-- else if(stack.lastOrNull()==want) stack.removeAt(stack.lastIndex)
                    i++; keyExpected=false
                }
                isBareStart(c) && !c.isDigit() && c!='-' -> {
                    var e=i+1; while(e<n && isBarePart(text[e])) e++
                    var q=e; while(q<n && (text[q]==' ' || text[q]=='\t')) q++
                    // Inline-table values must still be classified by value type;
                    // only the key position (start or after a comma) expects a key.
                    val isKey=(q<n && (text[q]=='=' || text[q]=='.')) || keyExpected
                    if(isKey) emit(i,e,TomlTokenKind.KEY) else classify(text,i,e)?.let { emit(i,e,it) }
                    i=e
                }
                c=='+' || c=='-' || c.isDigit() -> {
                    val numericKeyEnd=numericKeyEnd(text,i)
                    if(numericKeyEnd>i) { emit(i,numericKeyEnd,TomlTokenKind.KEY); i=numericKeyEnd; continue }
                    val specialEnd = specialFloatEnd(text,i)
                    if(specialEnd>i){emit(i,specialEnd,TomlTokenKind.NUMBER);i=specialEnd;continue}
                    val dt=dateTime(text,i)
                    val tm=if(dt>i)-1 else localTime(text,i)
                    if(dt>i) { emit(i,dt,TomlTokenKind.DATETIME); i=dt }
                    else if(tm>i) { emit(i,tm,TomlTokenKind.DATETIME); i=tm }
                    else { val e=number(text,i); if(e>i) { emit(i,e,TomlTokenKind.NUMBER); i=e } else i++ }
                }
                else -> { i++; if(c==',') keyExpected=stack.lastOrNull()=='{' }
            }
        }
    }
    private fun isBareStart(c:Char)=c.isLetterOrDigit()||c=='_'||c=='-'
    private fun isBarePart(c:Char)=c.isLetterOrDigit()||c=='_'||c=='-'
    private fun numericKeyEnd(t:CharSequence,p:Int):Int {
        if(p>=t.length || !isBareStart(t[p]))return p
        var i=p
        while(i<t.length && isBarePart(t[i]))i++
        while(i<t.length && t[i]=='.') { i++; if(i>=t.length || !isBareStart(t[i]))return p; while(i<t.length && isBarePart(t[i]))i++ }
        while(i<t.length && (t[i]==' ' || t[i]=='\t'))i++
        return if(i<t.length && t[i]=='=') { var e=i; while(e>p && (t[e-1]==' '||t[e-1]=='\t'))e--; e } else p
    }
    private fun looksAssignment(t:CharSequence,p:Int):Boolean { var i=p; while(i<t.length&&(isBarePart(t[i])||t[i]=='.'||t[i]==' '||t[i]=='\t'))i++; return i<t.length&&t[i]=='=' }
    private fun scanString(t:CharSequence,s:Int,q:Char):Int {
        val multi=s+2<t.length&&t[s+1]==q&&t[s+2]==q; var i=s+if(multi)3 else 1
        while(i<t.length) { if(multi&&i+2<t.length&&t[i]==q&&t[i+1]==q&&t[i+2]==q)return i+3; if(!multi&&t[i]==q)return i+1
            if(q=='"'&&t[i]=='\\'&&i+1<t.length){if(!multi&&(t[i+1]=='\n'||t[i+1]=='\r'))return i;i+=2;continue}; if(!multi&&(t[i]=='\n'||t[i]=='\r'))return i; i++ }
        return t.length
    }
    private fun specialFloatEnd(t:CharSequence,s:Int):Int {
        if(s>=t.length || (t[s]!='+' && t[s]!='-'))return s
        val word=if(s+1<t.length&&t[s+1]=='i')"inf" else if(s+1<t.length&&t[s+1]=='n')"nan" else return s
        val end=s+1+word.length
        return if(end<=t.length && t.subSequence(s,end).toString()==(t[s]+word) && boundary(t,end))end else s
    }
    private fun classify(t:CharSequence,s:Int,e:Int):TomlTokenKind? { val v=t.subSequence(s,e).toString(); return when(v){"true","false"->TomlTokenKind.BOOLEAN;"inf","+inf","-inf","nan","+nan","-nan"->TomlTokenKind.NUMBER;else->null} }
    private fun boundary(t:CharSequence,i:Int)=i>=t.length||t[i].isWhitespace()||t[i] in ",]}#"
    private fun takeDigits(t:CharSequence,p:Int,count:Int):Int { var i=p; repeat(count){if(i>=t.length||!t[i].isDigit())return -1;i++};return i }
    private fun dateTime(t:CharSequence,s:Int):Int {
        val y=takeDigits(t,s,4); if(y<0||y>=t.length||t[y]!='-')return -1
        val m=takeDigits(t,y+1,2); if(m<0||m>=t.length||t[m]!='-')return -1
        val d=takeDigits(t,m+1,2); if(d<0)return -1
        var i=d
        if(i<t.length&&(t[i]=='T'||t[i]=='t'||t[i]==' ')) {
            val sep=t[i]; val hh=takeDigits(t,i+1,2)
            if(hh>=0&&hh<t.length&&t[hh]==':') { val mm=takeDigits(t,hh+1,2)
                if(mm>=0&&mm<t.length&&t[mm]==':') { val ss=takeDigits(t,mm+1,2)
                    if(ss>=0) { i=ss
                        if(i<t.length&&t[i]=='.'){var f=i+1;while(f<t.length&&t[f].isDigit())f++;if(f>i+1)i=f}
                        if(i<t.length&&(t[i]=='Z'||t[i]=='z'))i++ else if(i<t.length&&(t[i]=='+'||t[i]=='-')) {
                            val oh=takeDigits(t,i+1,2); if(oh>=0&&oh<t.length&&t[oh]==':'){val om=takeDigits(t,oh+1,2);if(om>=0)i=om}
                        }
                        if(boundary(t,i))return i
                    }
                }
            }
            if(sep==' ')return if(boundary(t,d))d else -1
        }
        return if(boundary(t,d))d else -1
    }

    private fun localTime(t:CharSequence,s:Int):Int {
        val hh=takeDigits(t,s,2); if(hh<0||hh>=t.length||t[hh]!=':')return -1
        val mm=takeDigits(t,hh+1,2); if(mm<0||mm>=t.length||t[mm]!=':')return -1
        var i=takeDigits(t,mm+1,2); if(i<0)return -1
        if(i<t.length&&t[i]=='.'){var f=i+1;while(f<t.length&&t[f].isDigit())f++;if(f>i+1)i=f}
        return if(boundary(t,i))i else -1
    }
    private fun number(t:CharSequence,s:Int):Int {
        var i=s; if(t[i]=='+'||t[i]=='-')i++
        if(i+1<t.length&&t[i]=='0'&&t[i+1].lowercaseChar() in "xob") { val radix=t[i+1].lowercaseChar();i+=2;val b=i
            while(i<t.length&&(when(radix){'x'->t[i].isDigit()||t[i].lowercaseChar() in 'a'..'f'||t[i]=='_';'o'->t[i] in '0'..'7'||t[i]=='_';else->t[i]=='0'||t[i]=='1'||t[i]=='_' }))i++
            return if(i>b&&boundary(t,i))i else s
        }
        while(i<t.length&&(t[i].isDigit()||t[i]=='_'))i++
        if(i<t.length&&t[i]=='.'){i++;while(i<t.length&&(t[i].isDigit()||t[i]=='_'))i++}
        if(i<t.length&&(t[i]=='e'||t[i]=='E')){var q=i+1;if(q<t.length&&(t[q]=='+'||t[q]=='-'))q++;val b=q;while(q<t.length&&(t[q].isDigit()||t[q]=='_'))q++;if(q>b)i=q}
        return if(i>s&&boundary(t,i))i else s
    }
}

@OptIn(ExperimentalFoundationApi::class)
object TomlHighlightTransformation : OutputTransformation {
    private val styles = mapOf(
        TomlTokenKind.TABLE to SpanStyle(color=SyntaxColors.Type), TomlTokenKind.KEY to SpanStyle(color=SyntaxColors.Key),
        TomlTokenKind.STRING to SpanStyle(color=SyntaxColors.StringLiteral), TomlTokenKind.NUMBER to SpanStyle(color=SyntaxColors.Number),
        TomlTokenKind.BOOLEAN to SpanStyle(color=SyntaxColors.Keyword), TomlTokenKind.DATETIME to SpanStyle(color=SyntaxColors.Annotation),
        TomlTokenKind.COMMENT to SpanStyle(color=SyntaxColors.Comment), TomlTokenKind.OPERATOR to SpanStyle(color=SyntaxColors.Operator)
    )
    override fun TextFieldBuffer.transformOutput(){if(length==0||length>TomlSyntaxHighlighter.MAX_HIGHLIGHT_LENGTH)return;TomlSyntaxHighlighter.tokenize(asCharSequence()){s,e,k->addStyle(styles.getValue(k),s,e)}}
}
