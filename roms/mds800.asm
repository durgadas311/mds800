; combined Boot and Monitor ROM code for MDS-800

; ASCII characters
NIL	equ	0
CTRL$C	equ	3
CR	equ	13
LF	equ	10

RST0	equ	0c7h	; RST 0 instruction for breakpoints

pgexit	equ	0000h
iobyte	equ	0003h	; current iobyte
ramtop	equ	0004h	; low byte = PROM data XOR mask, high byte = top RAM page
iobyte2	equ	0006h	; iobyte as probed by BOOT ROM
bootbuf	equ	3000h	; assume at least 16K

; I/O ports
dstat	equ	078h	; read - bit0: device present, bit2: ready
diopbl	equ	079h	; write - iopb low
diopch	equ	07ah	; write - iopb high
drtype	equ	079h	; read - result type
drbyte	equ	07bh	; read - result byte
dreset	equ	07fh	; write - reset controller

dcread	equ	4	; disk ctrl READ command
dcwrit	equ	6	; WRITE command
dchome	equ	3	; seek track 0

dcredy	equ	0001b	; dstat floppy ready
dcfini	equ	0100b	; dstat I/O finished

promda	equ	0f0h	; write starts programming, read either uses /WAIT
			; or sets "start read" and polls promst for !uppbsy.
			; Monitor depends on /WAIT.
promah	equ	0f1h	; write (ah+ctrl bits) direct to programmer
promst	equ	0f1h	; read - direct from programmer
promal	equ	0f2h	; write - direct to programmer
intctl	equ	0f3h	; write - bit 7 enables TTY/CRT/PTP/PTR/LPT intrs
			;	  6:0 reset intr sources, see intsts
; promst
uppori	equ	10000000b	; orientation error
uppbrd	equ	01000000b	; board sense error
upphwe	equ	00100000b	; hardware error
uppadr	equ	00010000b	; address error
upppgm	equ	00001000b	; programming error
uppftp	equ	00000100b	; failed to program
uppok	equ	00000010b	; operation complete verified
uppbsy	equ	00000001b	; busy
; promah - CRSNAAAA
;	C = Control #1 (0)
;	R = start Read
;	S = Socket select (1=#1, 0=#2)
;	N = Nibble select (1=high)
;	A = Addr 11:8
; promal - AAAAAAAA
;	A = Addr 7:0

; values for promah:
promam	equ	0fffh	; PROM address mask - 4K max
promsX	equ	00ffh	; socket X (low byte unused?) C=0, R=0, S=0, N=0 (8-bit?)
promsY	equ	30f0h	; socket Y (low byte unused?) C=0, R=0, S=1, N=1
promsZ	equ	200fh	; socket Z (low byte unused?) C=0, R=0, S=1, N=0
; intctl:
chrie	equ	10000000b	; enable all char I/O interrupts

ttydat	equ	0f4h	; TTY: data
ttysts	equ	0f5h	; read
ttyctl	equ	0f5h	; write
crtdat	equ	0f6h	; CRT: data
crtsts	equ	0f7h	; read
crtctl	equ	0f7h	; write

ptdat	equ	0f8h	; read PTR, write PTP
ptsts	equ	0f9h	; read - PTR/P status
ptctl	equ	0f9h	; write - PTR/P control
intsts	equ	0fah	; read - TTY/CRT/PTP/PTR/LPT intr status
lptdat	equ	0fah	; write - LPT data out - inverted
lptsts	equ	0fbh	; read - LPT status
lptctl	equ	0fbh	; write - LPT control
; ptsts:
ptrrdy	equ	00000001b	; PTR data ready
ptrsys	equ	00000010b	; PTR system ready
ptprdy	equ	00000100b	; PTP ready for data
ptpsys	equ	00001000b	; PTP system ready
ptpchd	equ	00010000b	; PTP chad error
ptplow	equ	00100000b	; PTP tape low
; ptctl:
ttyadv	equ	00000010b	; TTY strobe reader one char
ptrdir	equ	00000100b	; PTR direction?
ptrdrv	equ	00001000b	; PTR strobe reader one char
ptpfor	equ	00010000b	; PTP forward? (direction?)
ptpadv	equ	00100000b	; PTP punch one char
; lptsts:
lptbsy	equ	00000001b	; LPT busy (data) - active low
lptstt	equ	00000010b	; LPT stat? error/ready?

;intsts and intctl:
ttyoi	equ	00000001b
ttyii	equ	00000010b
ptpoi	equ	00000100b
ptrii	equ	00001000b
crtoi	equ	00010000b
crtii	equ	00100000b
lptoi	equ	01000000b

imask	equ	0fch	; i8259 intr masks
rstint	equ	0fdh	; i8259 control/command

im0	equ	00000001b
im1	equ	00000010b
im2	equ	00000100b
im3	equ	00001000b
im4	equ	00010000b
im5	equ	00100000b
im6	equ	01000000b
im7	equ	10000000b
im1$7	equ	im1+im2+im3+im4+im5+im6+im7

fpsw	equ	0ffh	; read - front panel switches and state
fp1ms	equ	0001b	; 1mS clock?
bsw	equ	0010b	; BOOT switch

; Appears to have a sophisticated priority interrupt controller,
; (i3214) and interrupt stack (i3101A 16x4 RAM and 74191 stack ptr).

;     |      BOOT switch      |
;     |    OFF    |    ON     |
; ----+-----------+-----------+
; 0000|           | Bootstrap |
;     |           |    ROM    |
; ----+           +-----------+
; 0100|                       |
;     |          RAM          |
;     ~       (ex: 16K)       ~
;     |                       |
; 3EFF|   (end of user RAM)   | (default user stack)
; ----+ - - - - - - - - - - - +
; 3F00|       Monitor         |
;     |       Reserved        |
; 3FFF|         Page          | (max phys RAM addr)
; ----+-----------------------+
;     :\ \ \ \ \ \ \ \ \ \ \ \:
;     : \ \ \ \(empty)\ \ \ \ :
;     :\ \ \ \ \ \ \ \ \ \ \ \:
; ----+-----------------------+
; F800|                       |
;     |        Monitor        |
;     |          ROM          |
; FFFF|                       |
; ----+-----------------------+
;
; Page 0 (always writeable):
; 0000: JMP breakpoint	; exit from user program
; 0003: DB iobyte	; device assignments
; 0004: DW RAMend	; last byte of user memory
; 0008: RST 1 ...
; 0040: free...

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; Bootstrap ROM
; 256 byte i1702 EPROM at A36 on F.P. Ctrl PCB.
; Overrides RAM reads (only) when BOOT switch is ON.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

	org	0000h
	jmp	L0006		;; 0000: c3 06 00    ...

; overlays iobyte for reads
L0003:	db	00000000b	; default iobyte

	db	9,21		; datestamp, MM,DD

L0006:	di			;; 0006: f3          .
	mvi	a,012h	; ICW1: init i8259, SINGLE, vectors in xx00H
	out	rstint		;; 0009: d3 fd       ..
	xra	a		;; 000b: af          .
	xra	a	; ICW2: vectors in 00xxH
	out	imask		;; 000d: d3 fc       ..
	mvi	a,im1$7	; OCW1: intr masks
	out	imask		;; 0011: d3 fc       ..
	mvi	a,0		;; 0013: 3e 00       >.
	out	intctl	; /INT3 off
; look for end of RAM, starting with 0100h
	lxi	h,0000h		;; 0017: 21 00 00    ...
L001a:	inr	h		;; 001a: 24          $
	mov	a,m		;; 001b: 7e          ~
	cma			;; 001c: 2f          /
	mov	m,a		;; 001d: 77          w
	cmp	m		;; 001e: be          .
	cma			;; 001f: 2f          /
	mov	m,a		;; 0020: 77          w
	jz	L001a		;; 0021: ca 1a 00    ...
	dcx	h		;; 0024: 2b          +
	shld	ramtop		;; 0025: 22 04 00    "..
; copy savDE-L00ff to high memory page (xxC8H)
	lxi	b,savDE		;; 0028: 01 c8 00    ...
	mov	l,c		;; 002b: 69          i
	sphl			;; 002c: f9          .
L002d:	ldax	b		;; 002d: 0a          .
	mov	m,a		;; 002e: 77          w
	inr	c		;; 002f: 0c          .
	inr	l		;; 0030: 2c          ,
	jnz	L002d		;; 0031: c2 2d 00    .-.
; HL = top page of RAM
	mvi	l,LOW savSP+1	;; 0034: 2e d1       ..
	mov	m,h		;; 0036: 74          t
	dcr	m	; save very top page for monitor
	mvi	a,JMP		;; 0038: 3e c3       >.
; during BOOT, RAM is written while ROM is read
	sta	pgexit		;; 003a: 32 00 00    2..
	lxi	h,pgmxit	;; 003d: 21 0f ff    ...
	shld	pgexit+1	;; 0040: 22 01 00    "..
	mvi	a,04fh	; 1 stop, np, 8 data, 64x
	out	crtctl		;; 0045: d3 f7       ..
	mvi	a,0ceh	; 2 stop, np, 8 data, 16x
	out	ttyctl		;; 0049: d3 f5       ..
	mvi	a,027h	; RTS, RxEn, DTR, TxEn
	out	crtctl		;; 004d: d3 f7       ..
	out	ttyctl		;; 004f: d3 f5       ..
	xra	a		;; 0051: af          .
	out	ptctl		;; 0052: d3 f9       ..
	; check floppy inserted?
	in	dstat		;; 0054: db 78       .x
	rrc		; CY = dcredy
	jnc	L0068		;; 0057: d2 68 00    .h.
	; floppy is inserted - boot it
	mvi	a,LOW iopb
	out	diopbl		;; 005c: d3 79       .y
	xra	a	; HIGH iopb
	out	diopch		;; 005f: d3 7a       .z
	; wait for disk command finished
L0061:	in	dstat		;; 0061: db 78       .x
	ani	dcfini
	jz	L0061		;; 0065: ca 61 00    .a.

; alternately poll CRT and TTY for input.
; must see the blank-space character.
L0068:	lxi	h,iobyte	; reads access L0003
	mov	d,m		;; 006b: 56          V
	in	ttysts		;; 006c: db f5       ..
	ani	002h		;; 006e: e6 02       ..
	jz	L0078		;; 0070: ca 78 00    .x.
	in	ttydat		;; 0073: db f4       ..
	jmp	L0082		;; 0075: c3 82 00    ...

L0078:	inr	d		;; 0078: 14          .
	in	crtsts		;; 0079: db f7       ..
	ani	002h		;; 007b: e6 02       ..
	jz	L0068		;; 007d: ca 68 00    .h.
	in	crtdat		;; 0080: db f6       ..
L0082:	ani	07fh		;; 0082: e6 7f       ..
	cpi	' '		;; 0084: fe 20       .
	jnz	L0068		;; 0086: c2 68 00    .h.
	; found console, D is iobyte
	mov	m,d	; save iobyte in RAM
	mvi	l,LOW iobyte2	;; 008a: 2e 06       ..
	mov	m,d	; also save in L0006???
	; alternate monitor? based on port 78 bit 0...
	in	dstat		;; 008d: db 78       .x
	rrc	; CY = dcredy
	jc	bootbuf		;; 0090: da 00 30    ..0

; print 'MDS MONITOR, V2.0' to CON:
	mvi	l,LOW L00b1
	mvi	b,Z00b1		;; 0095: 06 15       ..
L0097:	mov	c,m		;; 0097: 4e          N
	mov	a,d		;; 0098: 7a          z
	rrc			;; 0099: 0f          .
	cnc	ttyout
	mov	a,d		;; 009d: 7a          z
	rrc			;; 009e: 0f          .
	cc	crtout
	inx	h		;; 00a2: 23          #
	dcr	b		;; 00a3: 05          .
	jnz	L0097		;; 00a4: c2 97 00    ...
	jmp	coldsf

; floppy boot iopb
iopb:	db	80h	; iocw: no update
	db	dcread	; floppy READ command
	db	26	; number of sectors to read (?)
	db	0	; track 0
	db	1	; sector 1 (first)
	dw	bootbuf	; DMA addr

L00b1:	db	CR,LF,'MDS MONITOR, V2.0'
	db	CR,LF
Z00b1	equ	$-L00b1

L00c6:	db	4fh,3eh	; 'O>' or L3e4f or 79,62 or DS 2

;------------------------------------------------------
; Template for monitor area of RAM.
; copied into high RAM page - xxC8H - also top of stack
;
monstk:	ds	0	; stop of stack for monitor
usrtop	equ	monstk-8	; monstk - 264

savDE:	dw	0ddeeh	; saved DE
savBC:	dw	0bbcch	; saved BC
savIM:	dw	0fe00h	; saved port FC
savPSW:	dw	0aaffh	; saved PSW
savSP:	dw	usrtop	; saved SP (modified for RAM size)
savlen	equ	$-savDE

; routine to resume user code
resume:	di			;; 00d2: f3          .
	pop	d		;; 00d3: d1          .
	pop	b		;; 00d4: c1          .
	pop	psw		;; 00d5: f1          .
	out	imask		;; 00d6: d3 fc       ..
	pop	psw		;; 00d8: f1          .
	pop	h		;; 00d9: e1          .
	sphl			;; 00da: f9          .
lxiHL:	lxi	h,01234h	; filled with saved HL
	ei			;; 00de: fb          .
jmpPC:	jmp	06789h		; filled with resume/GO addr (PC)

brkpts:	dw	0	; breakpoint addr #1
	db	0	; saved byte for bp#1
	dw	0	; breakpoint addr #2
	db	0	; saved byte for bp#2

; user-defined device driver hooks
uc1in:	jmp	pgexit		;; 00e8: c3 00 00    ...
uc1out:	jmp	pgexit		;; 00eb: c3 00 00    ...
ur1in:	jmp	pgexit		;; 00ee: c3 00 00    ...
ur2in:	jmp	pgexit		;; 00f1: c3 00 00    ...
up1out:	jmp	pgexit		;; 00f4: c3 00 00    ...
up2out:	jmp	pgexit		;; 00f7: c3 00 00    ...
ul1out:	jmp	pgexit		;; 00fa: c3 00 00    ...
uc1sts:	jmp	pgexit		;; 00fd: c3 00 00    ...

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; Monitor ROM
; 2K byte 8316 MROM at A7 on Monitor PCB.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
	org	0f800h

coldsf:	jmp	colds		;; f800: c3 30 f8    .0.
	jmp	conin		;; f803: conin
	jmp	rdrin		;; f806: rdrin
	jmp	conout		;; f809: conout
	jmp	punout		;; f80c: punout
	jmp	lstout		;; f80f: lstout
	jmp	consts		;; f812: consts
	jmp	Lfdcb		;; f815: chkcfg - get iobyte
	jmp	Lfdcf		;; f818: setcfg - set iobyte
	jmp	Lfdd4		;; f81b: ramsiz - 16K: B=3EH, A=0C0H
	jmp	Lfddc		;; f81e: iodef - define usr i/o - C=fnc vec, DE=rtn
	jmp	Lffc6		;; f821: nop - imm return

	db	9,21	; date code, MM,DD

abort:	lhld	ramtop		;; f826: 2a 04 00    *..
	mvi	l,LOW monstk	;; f829: 2e c8       ..
	sphl			;; f82b: f9          .
	call	conimm		;; f82c: cd 47 fd    .G.
	db	'#'
; cold start (RESET)
colds:	in	fpsw		;; f830: db ff       ..
	ani	bsw		;; f832: e6 02       ..
	jnz	colds	; wait for BOOT switch to be turned OFF
	ei			;; f837: fb          .
	call	coneol		;; f838: cd 18 fe    ...
	call	conimm		;; f83b: cd 47 fd    .G.
	db	'.'
	call	echin		;; f83f: cd c7 ff    ...
	sui	'A'		;; f842: d6 41       .A
	jm	colds		;; f844: fa 30 f8    .0.
	mvi	c,2	; default to 2 params?
	lxi	d,colds		;; f849: 11 30 f8    .0.
	push	d		;; f84c: d5          .
	lxi	h,cmdtab	;; f84d: 21 5f f8    ._.
	cpi	'X'-'A'+1	;; f850: fe 18       ..
	jp	abort		;; f852: f2 26 f8    .&.
	mov	e,a		;; f855: 5f          _
	mvi	d,0		;; f856: 16 00       ..
	dad	d		;; f858: 19          .
	dad	d		;; f859: 19          .
	mov	a,m		;; f85a: 7e          ~
	inx	h		;; f85b: 23          #
	mov	h,m		;; f85c: 66          f
	mov	l,a		;; f85d: 6f          o
	pchl			;; f85e: e9          .

cmdtab:	dw	Acmd	; A (Assign) <ldev>=<pdev> [see ldevtb]
	dw	Bcmd	; B (BNPF punch) <low-adr>,<high-adr>
	dw	Ccmd	; C (Compare PROM) <T/F><socket><low-adr>,<high-adr>
	dw	Dcmd	; D (Display mem) <low-adr>,<high-adr>
	dw	Ecmd	; E (HEX EOF to PUN:) <entry-adr> [after W command(s)]
	dw	Fcmd	; F (Fill mem) <low-adr>,<high-adr>,<byte>
	dw	Gcmd	; G (Go/exec) <addr>,<bp-1>,<bp-2>
	dw	Hcmd	; H (Hex math) <num-1>,<num-2>
	dw	abort	; I (invalid)
	dw	abort	; J (invalid)
	dw	abort	; K (invalid)
	dw	Lcmd	; L (Load BNPF) <low-adr>,<high-adr>
	dw	Mcmd	; M (Move mem) <low-adr>,<high-adr>,<dest-adr>
	dw	Ncmd	; N (NUL leader/trailer to PUN:) [60 NULs to PUN:]
	dw	abort	; O (invalid)
	dw	Pcmd	; P (Prog PROM) <T/F><socket><low-adr>,<high-adr>,<prom-adr>
	dw	Qcmd	; Q (Query I/O assignments)
	dw	Rcmd	; R (Read HEX on RDR:) <bias>
	dw	Scmd	; S (Substitute mem) <address>
	dw	Tcmd	; T (Transfer PROM) <T/F><socket><low-adr>,<high-adr>
	dw	abort	; U (invalid)
	dw	abort	; V (invalid)
	dw	Wcmd	; W (Write HEX on PUN:) <low-adr>,<high-adr>
	dw	Xcmd	; X (Examine all regs)
			; X (Modify regs) <reg-id> [see regtbl]

Acmd:	call	echin		;; f88f: cd c7 ff    ...
	lxi	h,ldevtb	;; f892: 21 dc f8    ...
	mvi	c,004h		;; f895: 0e 04       ..
Lf897:	cmp	m		;; f897: be          .
	inx	h		;; f898: 23          #
	jz	Lf8a6		;; f899: ca a6 f8    ...
	inx	h		;; f89c: 23          #
	inx	h		;; f89d: 23          #
	inx	h		;; f89e: 23          #
	dcr	c		;; f89f: 0d          .
	jnz	Lf897		;; f8a0: c2 97 f8    ...
	jmp	abort		;; f8a3: c3 26 f8    .&.

Lf8a6:	mov	b,m		;; f8a6: 46          F
	inx	h		;; f8a7: 23          #
	mov	e,m		;; f8a8: 5e          ^
	inx	h		;; f8a9: 23          #
	mov	d,m		;; f8aa: 56          V
	xchg			;; f8ab: eb          .
Lf8ac:	call	echin		;; f8ac: cd c7 ff    ...
	cpi	'='		;; f8af: fe 3d       .=
	jnz	Lf8ac		;; f8b1: c2 ac f8    ...
Lf8b4:	call	echin		;; f8b4: cd c7 ff    ...
	cpi	' '		;; f8b7: fe 20       .
	jz	Lf8b4		;; f8b9: ca b4 f8    ...
	mvi	c,004h		;; f8bc: 0e 04       ..
Lf8be:	cmp	m		;; f8be: be          .
	inx	h		;; f8bf: 23          #
	jz	Lf8cb		;; f8c0: ca cb f8    ...
	inx	h		;; f8c3: 23          #
	dcr	c		;; f8c4: 0d          .
	jnz	Lf8be		;; f8c5: c2 be f8    ...
	jmp	abort		;; f8c8: c3 26 f8    .&.

Lf8cb:	call	echin		;; f8cb: cd c7 ff    ...
	cpi	CR		;; f8ce: fe 0d       ..
	jnz	Lf8cb		;; f8d0: c2 cb f8    ...
	lda	iobyte		;; f8d3: 3a 03 00    :..
	ana	b		;; f8d6: a0          .
	ora	m		;; f8d7: b6          .
	sta	iobyte		;; f8d8: 32 03 00    2..
	ret			;; f8db: c9          .

; decoding of 'A' command parameters
ldevtb:	db	'C',11111100b ! dw contbl	; CON:
	db	'R',11110011b ! dw rdrtbl	; RDR:
	db	'P',11001111b ! dw puntbl	; PUN:
	db	'L',00111111b ! dw lsttbl	; LST:

contbl:	db	'T',00000000b	; TTY:
	db	'C',00000001b	; CRT:
	db	'B',00000010b	; BAT:
	db	'1',00000011b	; UC1:

rdrtbl:	db	'T',00000000b	; TTY:
	db	'P',00000100b	; PTR:
	db	'1',00001000b	; UR1:
	db	'2',00001100b	; UR2:

puntbl:	db	'T',00000000b	; TTY:
	db	'P',00010000b	; PTP:
	db	'1',00100000b	; UP1:
	db	'2',00110000b	; UP2:

lsttbl:	db	'T',00000000b	; TTY:
	db	'C',01000000b	; CRT:
	db	'L',10000000b	; LPT:
	db	'1',11000000b	; UL1:

Bcmd:	call	Lfe57	; C=2
	call	coneol
	call	nulout		;; f912: cd 98 fe    ...
	pop	d		;; f915: d1          .
	pop	h		;; f916: e1          .
Lf917:	call	punimm		;; f917: cd 4e fd    .N.
	db	'B'
	mvi	b,008h		;; f91b: 06 08       ..
	mov	a,m		;; f91d: 7e          ~
Lf91e:	rlc			;; f91e: 07          .
	push	psw		;; f91f: f5          .
	mvi	a,027h		;; f920: 3e 27       >'
	aci	000h		;; f922: ce 00       ..
	add	a		;; f924: 87          .
	mov	c,a		;; f925: 4f          O
	call	punout		;; f926: cd 52 fd    .R.
	pop	psw		;; f929: f1          .
	dcr	b		;; f92a: 05          .
	jnz	Lf91e		;; f92b: c2 1e f9    ...
	call	punimm		;; f92e: cd 4e fd    .N.
	db	'F'
Lf932:	call	punimm		;; f932: cd 4e fd    .N.
Lf935:	db	' '
	call	Lfe6a		;; f936: cd 6a fe    .j.
	jc	Lfa97		;; f939: da 97 fa    ...
	mov	a,l		;; f93c: 7d          }
	ani	003h		;; f93d: e6 03       ..
	cz	puneol		;; f93f: cc 06 ff    ...
	jmp	Lf917		;; f942: c3 17 f9    ...

Ccmd:	call	Lff94		;; f945: cd 94 ff    ...
	push	b		;; f948: c5          .
	mvi	c,2		;; f949: 0e 02       ..
	call	Lfe57		;; f94b: cd 57 fe    .W.
	pop	d	; prom addr
	pop	h	; mem addr
	pop	b	; prom socket word - B=ctl, C=msk
	push	b		;; f951: c5          .
	mvi	c,000h	; start addr 000
Lf954:	mov	a,b		;; f954: 78          x
	out	promah		;; f955: d3 f1       ..
	mov	a,c		;; f957: 79          y
	out	promal		;; f958: d3 f2       ..
	in	promda	; /WAIT until done
	push	h		;; f95c: e5          .
	lxi	h,ramtop	; data invert mask
	xra	m		;; f960: ae          .
	pop	h	; data to compare
	xra	m		;; f962: ae          .
	xthl		; prom socket word
	ana	l	; mask off data bits
	xthl			;; f965: e3          .
	jz	Lf987	; OK - data is same
	; report compare error
	push	b		;; f969: c5          .
	call	coneol		;; f96a: cd 18 fe    ...
	call	adrout		;; f96d: cd 7a fe    .z.
	call	space		;; f970: cd 11 fd    ...
	mov	a,m		;; f973: 7e          ~
	call	hexout		;; f974: cd 82 fe    ...
	call	space		;; f977: cd 11 fd    ...
	in	promda		;; f97a: db f0       ..
	call	hexout		;; f97c: cd 82 fe    ...
	in	promst		;; f97f: db f1       ..
	ani	uppok		;; f981: e6 02       ..
	jz	abort		;; f983: ca 26 f8    .&.
	pop	b		;; f986: c1          .
Lf987:	inx	b	; next prom addr
	call	Lfe6a	; check for buffer end
	jnc	Lf954	; keep going until end
	pop	b		;; f98e: c1          .
	ret			;; f98f: c9          .

Dcmd:	call	Lfe57	; C=2
	pop	d		;; f993: d1          .
	pop	h		;; f994: e1          .
Lf995:	call	lsteol		;; f995: cd 8e fe    ...
	call	Lfe21		;; f998: cd 21 fe    ...
Lf99b:	mvi	c,' '		;; f99b: 0e 20       .
	call	lstbrk		;; f99d: cd 7a fd    .z.
	mov	a,m		;; f9a0: 7e          ~
	call	Lfe29		;; f9a1: cd 29 fe    .).
	call	Lfe6a		;; f9a4: cd 6a fe    .j.
	jc	Lf9b3		;; f9a7: da b3 f9    ...
	mov	a,l		;; f9aa: 7d          }
	ani	00fh		;; f9ab: e6 0f       ..
	jnz	Lf99b		;; f9ad: c2 9b f9    ...
	jmp	Lf995		;; f9b0: c3 95 f9    ...

Lf9b3:	call	lsteol		;; f9b3: cd 8e fe    ...
	mvi	c,NIL		;; f9b6: 0e 00       ..
	call	lstbrk		;; f9b8: cd 7a fd    .z.
	ret			;; f9bb: c9          .

Ecmd:	dcr	c	; C=1
	call	Lfe57		;; f9bd: cd 57 fe    .W.
	call	punimm		;; f9c0: cd 4e fd    .N.
	db	':'
	xra	a		;; f9c4: af          .
	mov	d,a		;; f9c5: 57          W
	call	punhex		;; f9c6: cd e1 fe    ...
	pop	h		;; f9c9: e1          .
	call	punadr		;; f9ca: cd d9 fe    ...
	mvi	a,001h		;; f9cd: 3e 01       >.
	call	punhex		;; f9cf: cd e1 fe    ...
	xra	a		;; f9d2: af          .
	sub	d		;; f9d3: 92          .
	call	punhex		;; f9d4: cd e1 fe    ...
	jmp	Lfa97		;; f9d7: c3 97 fa    ...

Fcmd:	inr	c	; C=3
	call	Lfe57		;; f9db: cd 57 fe    .W.
	pop	b		;; f9de: c1          .
	pop	d		;; f9df: d1          .
	pop	h		;; f9e0: e1          .
Lf9e1:	mov	m,c		;; f9e1: 71          q
	call	Lfe6a		;; f9e2: cd 6a fe    .j.
	jnc	Lf9e1		;; f9e5: d2 e1 f9    ...
	ret			;; f9e8: c9          .

Gcmd:	lhld	ramtop		;; f9e9: 2a 04 00    *..
	mvi	l,LOW resume	;; f9ec: 2e d2       ..
	xthl			;; f9ee: e3          .
	call	trmin		;; f9ef: cd f7 fe    ...
	jz	Lfa01		;; f9f2: ca 01 fa    ...
	call	Lfea9		;; f9f5: cd a9 fe    ...
	xchg			;; f9f8: eb          .
	lhld	ramtop		;; f9f9: 2a 04 00    *..
	mvi	l,LOW jmpPC+2	;; f9fc: 2e e1       ..
	mov	m,d		;; f9fe: 72          r
	dcx	h		;; f9ff: 2b          +
	mov	m,e		;; fa00: 73          s
Lfa01:	jc	Lfa2e		;; fa01: da 2e fa    ...
	lxi	d,00002h	;; fa04: 11 02 00    ...
Lfa07:	call	conimm		;; fa07: cd 47 fd    .G.
	db	'-'
	call	adrin		;; fa0b: cd a3 fe    ...
	push	h		;; fa0e: e5          .
	inr	d		;; fa0f: 14          .
	jc	Lfa17		;; fa10: da 17 fa    ...
	dcr	e		;; fa13: 1d          .
	jnz	Lfa07		;; fa14: c2 07 fa    ...
Lfa17:	jnc	abort		;; fa17: d2 26 f8    .&.
	lhld	ramtop		;; fa1a: 2a 04 00    *..
	mvi	l,LOW brkpts	;; fa1d: 2e e2       ..
Lfa1f:	pop	b		;; fa1f: c1          .
	mov	m,c		;; fa20: 71          q
	inx	h		;; fa21: 23          #
	mov	m,b		;; fa22: 70          p
	inx	h		;; fa23: 23          #
	ldax	b		;; fa24: 0a          .
	mov	m,a		;; fa25: 77          w
	inx	h		;; fa26: 23          #
	mvi	a,RST0	; set breakpoint
	stax	b		;; fa29: 02          .
	dcr	d		;; fa2a: 15          .
	jnz	Lfa1f		;; fa2b: c2 1f fa    ...
Lfa2e:	call	coneol		;; fa2e: cd 18 fe    ...
	ret			;; fa31: c9          .

Hcmd:	call	Lfe57	; C=2
	call	coneol		;; fa35: cd 18 fe    ...
	pop	d		;; fa38: d1          .
	pop	h		;; fa39: e1          .
	push	h		;; fa3a: e5          .
	dad	d		;; fa3b: 19          .
	call	adrout		;; fa3c: cd 7a fe    .z.
	call	space		;; fa3f: cd 11 fd    ...
	pop	h		;; fa42: e1          .
	mov	a,l		;; fa43: 7d          }
	sub	e		;; fa44: 93          .
	mov	l,a		;; fa45: 6f          o
	mov	a,h		;; fa46: 7c          |
	sbb	d		;; fa47: 9a          .
	mov	h,a		;; fa48: 67          g
	call	adrout		;; fa49: cd 7a fe    .z.
	ret			;; fa4c: c9          .

Lcmd:	call	Lfe57	; C=2
	pop	d		;; fa50: d1          .
	pop	h		;; fa51: e1          .
Lfa52:	call	Lff8b		;; fa52: cd 8b ff    ...
	cpi	'B'		;; fa55: fe 42       .B
	jnz	Lfa52		;; fa57: c2 52 fa    .R.
	mvi	m,001h		;; fa5a: 36 01       6.
Lfa5c:	call	Lff8b		;; fa5c: cd 8b ff    ...
	cpi	'N'		;; fa5f: fe 4e       .N
	jz	Lfa69		;; fa61: ca 69 fa    .i.
	adi	-'P'		;; fa64: c6 b0       ..
	jnz	abort		;; fa66: c2 26 f8    .&.
Lfa69:	mov	a,m		;; fa69: 7e          ~
	ral			;; fa6a: 17          .
	mov	m,a		;; fa6b: 77          w
	jnc	Lfa5c		;; fa6c: d2 5c fa    .\.
	call	Lff8b		;; fa6f: cd 8b ff    ...
	cpi	'F'		;; fa72: fe 46       .F
	jnz	abort		;; fa74: c2 26 f8    .&.
	call	Lfe6a		;; fa77: cd 6a fe    .j.
	jnc	Lfa52		;; fa7a: d2 52 fa    .R.
	ret			;; fa7d: c9          .

Mcmd:	inr	c		;; fa7e: 0c          .
	call	Lfe57	; C=3
	pop	b		;; fa82: c1          .
	pop	d		;; fa83: d1          .
	pop	h		;; fa84: e1          .
Lfa85:	mov	a,m		;; fa85: 7e          ~
	stax	b		;; fa86: 02          .
	inx	b		;; fa87: 03          .
	call	Lfe6a		;; fa88: cd 6a fe    .j.
	jnc	Lfa85		;; fa8b: d2 85 fa    ...
	ret			;; fa8e: c9          .

Ncmd:	call	echin		;; fa8f: cd c7 ff    ...
	cpi	CR		;; fa92: fe 0d       ..
	jnz	abort		;; fa94: c2 26 f8    .&.
Lfa97:	call	nulout		;; fa97: cd 98 fe    ...
	call	punimm		;; fa9a: cd 4e fd    .N.
	db	NIL
	ret			;; fa9e: c9          .

Pcmd:	call	Lff94	; setup T/F (ramtop) and X/Y/Z (BC)
	push	b		;; faa2: c5          .
	mvi	c,3		;; faa3: 0e 03       ..
	call	Lfe57	; get 3 addresses, on stack
	call	coneol		;; faa8: cd 18 fe    ...
	pop	b	; BC = param3 - PROM addr
	pop	d	; DE = param2 - high addr
	pop	h	; HL = param1 - low addr
	mvi	a,HIGH promam	; limit to 0fffH (4K)
	ana	b		;; fab0: a0          .
	mov	b,a		;; fab1: 47          G
	pop	psw	; X/Y/Z - 00/30/20
	ora	b		;; fab3: b0          .
	mov	b,a		;; fab4: 47          G
Lfab5:	mov	a,b		;; fab5: 78          x
	out	promah		;; fab6: d3 f1       ..
	mov	a,c		;; fab8: 79          y
	out	promal		;; fab9: d3 f2       ..
	inx	b		;; fabb: 03          .
	lda	ramtop		;; fabc: 3a 04 00    :..
	xra	m		;; fabf: ae          .
	out	promda		;; fac0: d3 f0       ..
Lfac2:	in	promst		;; fac2: db f1       ..
	ani	uppbsy	; busy?
	jnz	Lfac2		;; fac6: c2 c2 fa    ...
	in	promst		;; fac9: db f1       ..
	ani	uppok	; done/OK
	jz	Lfad7		;; facd: ca d7 fa    ...
	call	Lfe6a	; check ++HL for reaching DE
	jnc	Lfab5		;; fad3: d2 b5 fa    ...
	ret		; done - success

Lfad7:	call	adrout		;; fad7: cd 7a fe    .z.
	jmp	abort		;; fada: c3 26 f8    .&.

Qcmd:	call	echin		;; fadd: cd c7 ff    ...
	cpi	CR		;; fae0: fe 0d       ..
	jnz	abort		;; fae2: c2 26 f8    .&.
	mvi	b,4	; 4 logical devices
	lxi	h,ldevtb	;; fae7: 21 dc f8    ...
Lfaea:	call	coneol		;; faea: cd 18 fe    ...
	mov	c,m		;; faed: 4e          N
	call	conbrk		;; faee: cd 13 fd    ...
	call	conimm		;; faf1: cd 47 fd    .G.
	db	'='
	inx	h		;; faf5: 23          #
	mov	a,m		;; faf6: 7e          ~
	cma			;; faf7: 2f          /
	mov	c,a		;; faf8: 4f          O
	inx	h		;; faf9: 23          #
	mov	e,m		;; fafa: 5e          ^
	inx	h		;; fafb: 23          #
	mov	d,m		;; fafc: 56          V
	inx	h		;; fafd: 23          #
	xchg			;; fafe: eb          .
	lda	iobyte		;; faff: 3a 03 00    :..
	ana	c		;; fb02: a1          .
	push	b		;; fb03: c5          .
	mvi	b,4	; 4 possible phys devices
Lfb06:	mov	c,m		;; fb06: 4e          N
	inx	h		;; fb07: 23          #
	cmp	m		;; fb08: be          .
	jz	Lfb11		;; fb09: ca 11 fb    ...
	inx	h		;; fb0c: 23          #
	dcr	b		;; fb0d: 05          .
	jnz	Lfb06		;; fb0e: c2 06 fb    ...
Lfb11:	call	conbrk		;; fb11: cd 13 fd    ...
	xchg			;; fb14: eb          .
	pop	b		;; fb15: c1          .
	dcr	b		;; fb16: 05          .
	jnz	Lfaea		;; fb17: c2 ea fa    ...
	ret			;; fb1a: c9          .

; R <bias> = Read HEX file from RDR: (C=2)
Rcmd:	dcr	c	; C=1
	call	Lfe57	; get 1 address, on stack
	call	coneol		;; fb1f: cd 18 fe    ...
Lfb22:	call	Lff8b		;; fb22: cd 8b ff    ...
	cpi	':'		;; fb25: fe 3a       .:
	jnz	Lfb22		;; fb27: c2 22 fb    .".
	xra	a		;; fb2a: af          .
	mov	d,a	; init checksum
	call	Lfdf5	; num bytes
	jz	Lfb67	; EOF record
	mov	e,a		;; fb32: 5f          _
	call	Lfdf5	; addr high
	mov	h,a		;; fb36: 67          g
	call	Lfdf5	; addr low
	mov	l,a		;; fb3a: 6f          o
	call	Lfdf5	; rec type - discard
	mov	c,e	; C=E=num bytes
	push	h		;; fb3f: e5          .
	lxi	h,-256	; buffer on stack
	dad	sp		;; fb43: 39          9
Lfb44:	call	Lfdf5	; data byte
	mov	m,a		;; fb47: 77          w
	inx	h		;; fb48: 23          #
	dcr	e		;; fb49: 1d          .
	jnz	Lfb44		;; fb4a: c2 44 fb    .D.
	call	Lfdf5	; checksum
	jnz	abort		;; fb50: c2 26 f8    .&.
	pop	d	; DE=load addr
	xthl		; TOS=buf end, HL=bias
	xchg		; DE=bias, HL=load addr
	dad	d	; HL+=bias (mem addr)
	mvi	b,0
	dad	b	; HL+=num bytes (end of mem adr)
	xchg		; DE=mem addr end, HL=bias
	xthl		; TOS=bias, HL=buf end
; copy buf backwards into memory...
Lfb5c:	dcx	h		;; fb5c: 2b          +
	mov	a,m		;; fb5d: 7e          ~
	dcx	d		;; fb5e: 1b          .
	stax	d		;; fb5f: 12          .
	dcr	c		;; fb60: 0d          .
	jnz	Lfb5c		;; fb61: c2 5c fb    .\.
	jmp	Lfb22	; look for next line

; HEX EOF record
Lfb67:	lhld	ramtop		;; fb67: 2a 04 00    *..
	mvi	l,LOW jmpPC+2	;; fb6a: 2e e1       ..
	call	Lfdf5	; entry high
	mov	m,a		;; fb6f: 77          w
	dcx	h		;; fb70: 2b          +
	call	Lfdf5	; entry low
	mov	m,a		;; fb74: 77          w
	pop	h	; discard bias
	ret

Scmd:	call	adrin		;; fb77: cd a3 fe    ...
	rc			;; fb7a: d8          .
Lfb7b:	mov	a,m		;; fb7b: 7e          ~
	call	hexout		;; fb7c: cd 82 fe    ...
	call	conimm		;; fb7f: cd 47 fd    .G.
	db	'-'
	call	trmin		;; fb83: cd f7 fe    ...
	rc			;; fb86: d8          .
	jz	Lfb91		;; fb87: ca 91 fb    ...
	xchg			;; fb8a: eb          .
	call	Lfea9		;; fb8b: cd a9 fe    ...
	xchg			;; fb8e: eb          .
	mov	m,e		;; fb8f: 73          s
	rc			;; fb90: d8          .
Lfb91:	inx	h		;; fb91: 23          #
	jmp	Lfb7b		;; fb92: c3 7b fb    .{.

Tcmd:	call	Lff94		;; fb95: cd 94 ff    ...
	mvi	c,0		;; fb98: 0e 00       ..
	push	b		;; fb9a: c5          .
	mvi	c,2		;; fb9b: 0e 02       ..
	call	Lfe57		;; fb9d: cd 57 fe    .W.
	pop	d		;; fba0: d1          .
	pop	h		;; fba1: e1          .
	pop	b		;; fba2: c1          .
Lfba3:	mov	a,b		;; fba3: 78          x
	out	promah		;; fba4: d3 f1       ..
	mov	a,c		;; fba6: 79          y
	out	promal		;; fba7: d3 f2       ..
	inx	b		;; fba9: 03          .
	in	promda		;; fbaa: db f0       ..
	push	h		;; fbac: e5          .
	lxi	h,ramtop	;; fbad: 21 04 00    ...
	xra	m		;; fbb0: ae          .
	pop	h		;; fbb1: e1          .
	mov	m,a		;; fbb2: 77          w
	in	promst		;; fbb3: db f1       ..
	ani	uppok		;; fbb5: e6 02       ..
	jz	abort		;; fbb7: ca 26 f8    .&.
	call	Lfe6a		;; fbba: cd 6a fe    .j.
	jnc	Lfba3		;; fbbd: d2 a3 fb    ...
	ret			;; fbc0: c9          .

Wcmd:	call	Lfe57	; C=2
	call	coneol		;; fbc4: cd 18 fe    ...
	pop	d		;; fbc7: d1          .
	pop	h		;; fbc8: e1          .
Lfbc9:	call	punimm		;; fbc9: cd 4e fd    .N.
	db	':'
	; count number of bytes in record
	lxi	b,16		;; fbcd: 01 10 00    ...
	push	h		;; fbd0: e5          .
Lfbd1:	inr	b		;; fbd1: 04          .
	dcr	c		;; fbd2: 0d          .
	jz	Lfbdc		;; fbd3: ca dc fb    ...
	call	Lfe6a	; check ++HL against DE
	jnc	Lfbd1		;; fbd9: d2 d1 fb    ...
; B=num bytes
Lfbdc:	pop	h		;; fbdc: e1          .
	push	d		;; fbdd: d5          .
	mvi	d,0		;; fbde: 16 00       ..
	mov	a,b		;; fbe0: 78          x
	call	punhex	; num bytes
	call	punadr	; addr H/L
	xra	a
	call	punhex	; rec type (00)
Lfbeb:	mov	a,m		;; fbeb: 7e          ~
	call	punhex	; data bytes
	inx	h		;; fbef: 23          #
	dcr	b		;; fbf0: 05          .
	jnz	Lfbeb		;; fbf1: c2 eb fb    ...
	xra	a		;; fbf4: af          .
	sub	d		;; fbf5: 92          .
	call	punhex	; checksum
	pop	d		;; fbf9: d1          .
	dcx	h		;; fbfa: 2b          +
	call	puneol		;; fbfb: cd 06 ff    ...
	call	Lfe6a	; check ++HL against DE
	jnc	Lfbc9	; do another record
	ret

Xcmd:	lxi	h,regtbl	;; fc05: 21 60 fc    .`.
	call	trmin		;; fc08: cd f7 fe    ...
	jc	regall	; CR hit
	mvi	c,12		;; fc0e: 0e 0c       ..
Lfc10:	cmp	m		;; fc10: be          .
	jz	Lfc1e	; matched reg-id
	inx	h		;; fc14: 23          #
	inx	h		;; fc15: 23          #
	inx	h		;; fc16: 23          #
	dcr	c		;; fc17: 0d          .
	jnz	Lfc10		;; fc18: c2 10 fc    ...
	jmp	abort		;; fc1b: c3 26 f8    .&.

Lfc1e:	call	space		;; fc1e: cd 11 fd    ...
Lfc21:	call	regout		;; fc21: cd 43 fe    .C.
	call	conimm		;; fc24: cd 47 fd    .G.
	db	'-'
	call	trmin		;; fc28: cd f7 fe    ...
	rc			;; fc2b: d8          .
	jz	Lfc3f		;; fc2c: ca 3f fc    .?.
	push	h		;; fc2f: e5          .
	push	b		;; fc30: c5          .
	call	Lfea9		;; fc31: cd a9 fe    ...
	mov	a,l		;; fc34: 7d          }
	stax	d		;; fc35: 12          .
	pop	psw		;; fc36: f1          .
	ora	a		;; fc37: b7          .
	jm	Lfc3e		;; fc38: fa 3e fc    .>.
	inx	d		;; fc3b: 13          .
	mov	a,h		;; fc3c: 7c          |
	stax	d		;; fc3d: 12          .
Lfc3e:	pop	h		;; fc3e: e1          .
Lfc3f:	xra	a		;; fc3f: af          .
	ora	m		;; fc40: b6          .
	rm			;; fc41: f8          .
	mov	a,b		;; fc42: 78          x
	cpi	CR		;; fc43: fe 0d       ..
	rz			;; fc45: c8          .
	jmp	Lfc21		;; fc46: c3 21 fc    ...

; HL=regtbl
regall:	call	coneol		;; fc49: cd 18 fe    ...
Lfc4c:	call	space		;; fc4c: cd 11 fd    ...
	xra	a		;; fc4f: af          .
	ora	m		;; fc50: b6          .
	rm		; end of table marked 0ffh
	mov	c,m		;; fc52: 4e          N
	call	conbrk		;; fc53: cd 13 fd    ...
	call	conimm		;; fc56: cd 47 fd    .G.
	db	'='
	call	regout		;; fc5a: cd 43 fe    .C.
	jmp	Lfc4c		;; fc5d: c3 4c fc    .L.

; decoding of X command parameters
; how/where to access saved registers
regtbl:	db 'A' ! db LOW savPSW+1! db 0	; A reg
	db 'B' ! db LOW savBC+1 ! db 0	; B reg
	db 'C' ! db LOW savBC   ! db 0	; C reg
	db 'D' ! db LOW savDE+1 ! db 0	; D reg
	db 'E' ! db LOW savDE   ! db 0	; E reg
	db 'F' ! db LOW savPSW  ! db 0	; flags
	db 'H' ! db LOW lxiHL+2 ! db 0	; H reg
	db 'I' ! db LOW savIM+1 ! db 0	; imask
	db 'L' ! db LOW lxiHL+1 ! db 0	; L reg
	db 'M' ! db LOW lxiHL+2 ! db 1	; HL (M ptr)
	db 'P' ! db LOW jmpPC+2 ! db 1	; PC
	db 'S' ! db LOW savSP+1 ! db 1	; SP
	db 0ffh	; termination

conin:	lda	iobyte
	ani	003h		;; fc88: e6 03       ..
	jnz	Lfc97		;; fc8a: c2 97 fc    ...
Lfc8d:	in	ttysts		;; fc8d: db f5       ..
	ani	002h		;; fc8f: e6 02       ..
	jz	Lfc8d		;; fc91: ca 8d fc    ...
	in	ttydat		;; fc94: db f4       ..
	ret			;; fc96: c9          .

Lfc97:	cpi	001h		;; fc97: fe 01       ..
	jnz	Lfca6		;; fc99: c2 a6 fc    ...
Lfc9c:	in	crtsts		;; fc9c: db f7       ..
	ani	002h		;; fc9e: e6 02       ..
	jz	Lfc9c		;; fca0: ca 9c fc    ...
	in	crtdat		;; fca3: db f6       ..
	ret			;; fca5: c9          .

Lfca6:	cpi	002h		;; fca6: fe 02       ..
	jz	rdrin		;; fca8: ca b8 fc    ...
	mvi	a,LOW uc1in	;; fcab: 3e e8       >.
	jmp	usrvec		;; fcad: c3 0a fd    ...

brkchk:	call	consts		;; fcb0: cd a4 fd    ...
	ora	a		;; fcb3: b7          .
	rz			;; fcb4: c8          .
	jmp	echin		;; fcb5: c3 c7 ff    ...

; get char from RDR:, return CY on timeout
rdrin:	push	h		;; fcb8: e5          .
	lxi	h,iobyte	;; fcb9: 21 03 00    ...
	mov	a,m		;; fcbc: 7e          ~
	ani	00ch		;; fcbd: e6 0c       ..
	jnz	Lfcdf		;; fcbf: c2 df fc    ...
; TTY PTR
	mvi	a,ttyadv	;; fcc2: 3e 02       >.
	out	ptctl		;; fcc4: d3 f9       ..
	mvi	h,250	; approx 1/4 sec
Lfcc8:	in	ttysts		;; fcc8: db f5       ..
	ani	002h		;; fcca: e6 02       ..
	jnz	Lfcda		;; fccc: c2 da fc    ...
	call	wt1ms	; wait for 1ms tick
	dcr	h		;; fcd2: 25          %
	jnz	Lfcc8		;; fcd3: c2 c8 fc    ...
Lfcd6:	xra	a		;; fcd6: af          .
	stc			;; fcd7: 37          7
	pop	h		;; fcd8: e1          .
	ret			;; fcd9: c9          .

Lfcda:	in	ttydat		;; fcda: db f4       ..
	ora	a		;; fcdc: b7          .
	pop	h		;; fcdd: e1          .
	ret			;; fcde: c9          .

Lfcdf:	cpi	004h		;; fcdf: fe 04       ..
	jnz	Lfd00		;; fce1: c2 00 fd    ...
; high speed reader - PTR:
	mvi	a,ptrdrv	;; fce4: 3e 08       >.
	out	ptctl		;; fce6: d3 f9       ..
	mvi	h,250	; approx 1/4 sec
Lfcea:	in	ptsts		;; fcea: db f9       ..
	ani	ptrrdy		;; fcec: e6 01       ..
	jnz	Lfcfb		;; fcee: c2 fb fc    ...
	call	wt1ms	; wait for 1ms tick
	dcr	h		;; fcf4: 25          %
	jnz	Lfcea		;; fcf5: c2 ea fc    ...
	jmp	Lfcd6		;; fcf8: c3 d6 fc    ...

Lfcfb:	in	ptdat		;; fcfb: db f8       ..
	ora	a		;; fcfd: b7          .
	pop	h		;; fcfe: e1          .
	ret			;; fcff: c9          .

Lfd00:	pop	h		;; fd00: e1          .
	cpi	008h		;; fd01: fe 08       ..
	mvi	a,LOW ur1in	;; fd03: 3e ee       >.
	jz	usrvec		;; fd05: ca 0a fd    ...
	mvi	a,LOW ur2in	;; fd08: 3e f1       >.
; call a user device vector (A={e8,eb,ee,f1,f4,f7,fa,fd})
usrvec:	push	h		;; fd0a: e5          .
	lhld	ramtop		;; fd0b: 2a 04 00    *..
	mov	l,a		;; fd0e: 6f          o
	xthl			;; fd0f: e3          .
	ret			;; fd10: c9          .

space:	mvi	c,' '		;; fd11: 0e 20       .
conbrk:	lda	iobyte		;; fd13: 3a 03 00    :..
	ani	003h		;; fd16: e6 03       ..
	cpi	002h		;; fd18: fe 02       ..
	cnz	brkchk		;; fd1a: c4 b0 fc    ...
; CON: out from C
conout:	lda	iobyte		;; fd1d: 3a 03 00    :..
	ani	003h		;; fd20: e6 03       ..
	jnz	Lfd30		;; fd22: c2 30 fd    .0.
; TTY: out
ttyout:	in	ttysts		;; fd25: db f5       ..
	ani	001h		;; fd27: e6 01       ..
	jz	ttyout		;; fd29: ca 25 fd    .%.
	mov	a,c		;; fd2c: 79          y
	out	ttydat		;; fd2d: d3 f4       ..
	ret			;; fd2f: c9          .

Lfd30:	cpi	002h		;; fd30: fe 02       ..
	jz	lstout		;; fd32: ca 84 fd    ...
	cpi	001h		;; fd35: fe 01       ..
	mvi	a,LOW uc1out	;; fd37: 3e eb       >.
	jnz	usrvec		;; fd39: c2 0a fd    ...
crtout:	in	crtsts		;; fd3c: db f7       ..
	ani	001h		;; fd3e: e6 01       ..
	jz	crtout		;; fd40: ca 3c fd    .<.
	mov	a,c		;; fd43: 79          y
	out	crtdat		;; fd44: d3 f6       ..
	ret			;; fd46: c9          .

conimm:	xthl			;; fd47: e3          .
	mov	c,m		;; fd48: 4e          N
	inx	h		;; fd49: 23          #
	xthl			;; fd4a: e3          .
	jmp	conbrk		;; fd4b: c3 13 fd    ...

punimm:	xthl			;; fd4e: e3          .
	mov	c,m		;; fd4f: 4e          N
	inx	h		;; fd50: 23          #
	xthl			;; fd51: e3          .
punout:	lda	iobyte		;; fd52: 3a 03 00    :..
	ani	030h		;; fd55: e6 30       .0
	jz	ttyout		;; fd57: ca 25 fd    .%.
	cpi	010h		;; fd5a: fe 10       ..
	jnz	Lfd6e		;; fd5c: c2 6e fd    .n.
Lfd5f:	in	ptsts		;; fd5f: db f9       ..
	ani	ptprdy		;; fd61: e6 04       ..
	jz	Lfd5f		;; fd63: ca 5f fd    ._.
	mov	a,c		;; fd66: 79          y
	out	ptdat		;; fd67: d3 f8       ..
	mvi	a,ptpadv	;; fd69: 3e 20       >
	out	ptctl		;; fd6b: d3 f9       ..
	ret			;; fd6d: c9          .

Lfd6e:	cpi	020h		;; fd6e: fe 20       .
	mvi	a,LOW up1out	;; fd70: 3e f4       >.
	jz	usrvec		;; fd72: ca 0a fd    ...
	mvi	a,LOW up2out	;; fd75: 3e f7       >.
	jmp	usrvec		;; fd77: c3 0a fd    ...

; output char to LST:, check for console break
lstbrk:	lda	iobyte		;; fd7a: 3a 03 00    :..
	ani	003h		;; fd7d: e6 03       ..
	cpi	002h		;; fd7f: fe 02       ..
	cnz	brkchk		;; fd81: c4 b0 fc    ...
lstout:	lda	iobyte		;; fd84: 3a 03 00    :..
	ani	0c0h		;; fd87: e6 c0       ..
	jz	ttyout		;; fd89: ca 25 fd    .%.
	cpi	040h		;; fd8c: fe 40       .@
	jz	crtout		;; fd8e: ca 3c fd    .<.
	cpi	0c0h		;; fd91: fe c0       ..
	mvi	a,LOW ul1out	;; fd93: 3e fa       >.
	jz	usrvec		;; fd95: ca 0a fd    ...
Lfd98:	in	lptsts		;; fd98: db fb       ..
	ani	lptbsy		;; fd9a: e6 01       ..
	jz	Lfd98		;; fd9c: ca 98 fd    ...
	mov	a,c		;; fd9f: 79          y
	cma			;; fda0: 2f          /
	out	lptdat		;; fda1: d3 fa       ..
	ret			;; fda3: c9          .

consts:	lda	iobyte		;; fda4: 3a 03 00    :..
	ani	003h		;; fda7: e6 03       ..
	jnz	Lfdb1		;; fda9: c2 b1 fd    ...
	in	ttysts		;; fdac: db f5       ..
	jmp	Lfdb8		;; fdae: c3 b8 fd    ...

Lfdb1:	cpi	001h		;; fdb1: fe 01       ..
	jnz	Lfdbf		;; fdb3: c2 bf fd    ...
	in	crtsts		;; fdb6: db f7       ..
Lfdb8:	ani	002h		;; fdb8: e6 02       ..
	mvi	a,000h		;; fdba: 3e 00       >.
Lfdbc:	rz			;; fdbc: c8          .
	cma			;; fdbd: 2f          /
	ret			;; fdbe: c9          .

Lfdbf:	cpi	002h		;; fdbf: fe 02       ..
	mvi	a,0ffh		;; fdc1: 3e ff       >.
	jz	Lfdbc		;; fdc3: ca bc fd    ...
	mvi	a,LOW uc1sts	;; fdc6: 3e fd       >.
	jmp	usrvec		;; fdc8: c3 0a fd    ...

Lfdcb:	lda	iobyte		;; fdcb: 3a 03 00    :..
	ret			;; fdce: c9          .

Lfdcf:	mov	a,c		;; fdcf: 79          y
	sta	iobyte		;; fdd0: 32 03 00    2..
	ret			;; fdd3: c9          .

Lfdd4:	lda	ramtop+1
	dcr	a		; last page for users
	mov	b,a
	mvi	a,LOW usrtop	; offset of user top RAM
	ret

; Define user I/O routine. C=vector, DE=routine
Lfddc:	push	h		;; fddc: e5          .
	push	b		;; fddd: c5          .
	lhld	ramtop		;; fdde: 2a 04 00    *..
	mvi	l,LOW uc1in+1	;; fde1: 2e e9       ..
	mov	a,c		;; fde3: 79          y
	cpi	008h		;; fde4: fe 08       ..
	jnc	abort		;; fde6: d2 26 f8    .&.
	add	c		;; fde9: 81          .
	add	c		;; fdea: 81          .
	mov	c,a		;; fdeb: 4f          O
	mvi	b,000h		;; fdec: 06 00       ..
	dad	b	; point to vector
	mov	m,e	; install routine from DE
	inx	h		;; fdf0: 23          #
	mov	m,d		;; fdf1: 72          r
	pop	b		;; fdf2: c1          .
	pop	h		;; fdf3: e1          .
	ret			;; fdf4: c9          .

Lfdf5:	push	b		;; fdf5: c5          .
	call	Lff8b		;; fdf6: cd 8b ff    ...
	call	a2hex		;; fdf9: cd c7 fe    ...
	rlc			;; fdfc: 07          .
	rlc			;; fdfd: 07          .
	rlc			;; fdfe: 07          .
	rlc			;; fdff: 07          .
	mov	c,a		;; fe00: 4f          O
	call	Lff8b		;; fe01: cd 8b ff    ...
	call	a2hex		;; fe04: cd c7 fe    ...
	ora	c		;; fe07: b1          .
	mov	c,a		;; fe08: 4f          O
	add	d		;; fe09: 82          .
	mov	d,a		;; fe0a: 57          W
	mov	a,c		;; fe0b: 79          y
	pop	b		;; fe0c: c1          .
	ret			;; fe0d: c9          .

hex2a:	ani	00fh		;; fe0e: e6 0f       ..
	adi	090h		;; fe10: c6 90       ..
	daa			;; fe12: 27          '
	aci	040h		;; fe13: ce 40       .@
	daa			;; fe15: 27          '
	mov	c,a		;; fe16: 4f          O
	ret			;; fe17: c9          .

coneol:	call	conimm		;; fe18: cd 47 fd    .G.
	db	CR
	call	conimm		;; fe1c: cd 47 fd    .G.
	db	LF
	ret			;; fe20: c9          .

Lfe21:	mov	a,h		;; fe21: 7c          |
	call	Lfe29		;; fe22: cd 29 fe    .).
	mov	a,l		;; fe25: 7d          }
	jmp	Lfe29		;; fe26: c3 29 fe    .).

Lfe29:	push	psw		;; fe29: f5          .
	rrc			;; fe2a: 0f          .
	rrc			;; fe2b: 0f          .
	rrc			;; fe2c: 0f          .
	rrc			;; fe2d: 0f          .
	call	hex2a		;; fe2e: cd 0e fe    ...
	call	lstbrk		;; fe31: cd 7a fd    .z.
	pop	psw		;; fe34: f1          .
	call	hex2a		;; fe35: cd 0e fe    ...
	jmp	lstbrk		;; fe38: c3 7a fd    .z.

wt1ms:	in	fpsw		;; fe3b: db ff       ..
	ani	fp1ms		;; fe3d: e6 01       ..
	jz	wt1ms		;; fe3f: ca 3b fe    .;.
	ret			;; fe42: c9          .

; reg-id match
regout:	inx	h		;; fe43: 23          #
	mov	e,m		;; fe44: 5e          ^
	lda	ramtop+1	;; fe45: 3a 05 00    :..
	mov	d,a		;; fe48: 57          W
	inx	h		;; fe49: 23          #
	mov	b,m		;; fe4a: 46          F
	inx	h		;; fe4b: 23          #
; params = DE=reg adr, B=num-bytes (-1)
	ldax	d		;; fe4c: 1a          .
	call	hexout		;; fe4d: cd 82 fe    ...
	dcr	b		;; fe50: 05          .
	rm			;; fe51: f8          .
	dcx	d		;; fe52: 1b          .
	ldax	d		;; fe53: 1a          .
	jmp	hexout		;; fe54: c3 82 fe    ...

; input C addresses from console, put on stack
Lfe57:	call	adrin		;; fe57: cd a3 fe    ...
	xthl			;; fe5a: e3          .
	push	h		;; fe5b: e5          .
	dcr	c		;; fe5c: 0d          .
	jnc	Lfe64		;; fe5d: d2 64 fe    .d.
	jnz	abort		;; fe60: c2 26 f8    .&.
	ret			;; fe63: c9          .

Lfe64:	jnz	Lfe57		;; fe64: c2 57 fe    .W.
	jmp	abort		;; fe67: c3 26 f8    .&.

; ++HL, compare DE :: HL, unless HL=0 (overflow?)
Lfe6a:	inx	h		;; fe6a: 23          #
	mov	a,h		;; fe6b: 7c          |
	ora	l		;; fe6c: b5          .
	stc			;; fe6d: 37          7
	rz			;; fe6e: c8          .
	mov	a,e		;; fe6f: 7b          {
	sub	l		;; fe70: 95          .
	mov	a,d		;; fe71: 7a          z
	sbb	h		;; fe72: 9c          .
	ret			;; fe73: c9          .

Lfe74:	call	hex2a		;; fe74: cd 0e fe    ...
	jmp	conbrk		;; fe77: c3 13 fd    ...

adrout:	mov	a,h		;; fe7a: 7c          |
	call	hexout		;; fe7b: cd 82 fe    ...
	mov	a,l		;; fe7e: 7d          }
	jmp	hexout		;; fe7f: c3 82 fe    ...

hexout:	push	psw		;; fe82: f5          .
	rrc			;; fe83: 0f          .
	rrc			;; fe84: 0f          .
	rrc			;; fe85: 0f          .
	rrc			;; fe86: 0f          .
	call	Lfe74		;; fe87: cd 74 fe    .t.
	pop	psw		;; fe8a: f1          .
	jmp	Lfe74		;; fe8b: c3 74 fe    .t.

lsteol:	mvi	c,CR		;; fe8e: 0e 0d       ..
	call	lstbrk		;; fe90: cd 7a fd    .z.
	mvi	c,LF		;; fe93: 0e 0a       ..
	jmp	lstbrk		;; fe95: c3 7a fd    .z.

nulout:	mvi	b,60		;; fe98: 06 3c       .<
Lfe9a:	call	punimm		;; fe9a: cd 4e fd    .N.
	db	NIL
	dcr	b		;; fe9e: 05          .
	jnz	Lfe9a		;; fe9f: c2 9a fe    ...
	ret			;; fea2: c9          .

adrin:	call	trmin		;; fea3: cd f7 fe    ...
	jz	abort		;; fea6: ca 26 f8    .&.
Lfea9:	lxi	h,00000h	;; fea9: 21 00 00    ...
Lfeac:	mov	b,a		;; feac: 47          G
	call	a2hex		;; fead: cd c7 fe    ...
	jc	Lfebf		;; feb0: da bf fe    ...
	dad	h		;; feb3: 29          )
	dad	h		;; feb4: 29          )
	dad	h		;; feb5: 29          )
	dad	h		;; feb6: 29          )
	ora	l		;; feb7: b5          .
	mov	l,a		;; feb8: 6f          o
	call	echin		;; feb9: cd c7 ff    ...
	jmp	Lfeac		;; febc: c3 ac fe    ...

Lfebf:	mov	a,b		;; febf: 78          x
	call	Lfefa		;; fec0: cd fa fe    ...
	jnz	abort		;; fec3: c2 26 f8    .&.
	ret			;; fec6: c9          .

a2hex:	sui	'0'		;; fec7: d6 30       .0
	rc			;; fec9: d8          .
	adi	0e9h		;; feca: c6 e9       ..
	rc			;; fecc: d8          .
	adi	6		;; fecd: c6 06       ..
	jp	Lfed5		;; fecf: f2 d5 fe    ...
	adi	7		;; fed2: c6 07       ..
	rc			;; fed4: d8          .
Lfed5:	adi	10		;; fed5: c6 0a       ..
	ora	a		;; fed7: b7          .
	ret			;; fed8: c9          .

punadr:	mov	a,h		;; fed9: 7c          |
	call	punhex		;; feda: cd e1 fe    ...
	mov	a,l		;; fedd: 7d          }
	jmp	punhex		;; fede: c3 e1 fe    ...

punhex:	mov	e,a		;; fee1: 5f          _
	rrc			;; fee2: 0f          .
	rrc			;; fee3: 0f          .
	rrc			;; fee4: 0f          .
	rrc			;; fee5: 0f          .
	call	hex2a		;; fee6: cd 0e fe    ...
	call	punout		;; fee9: cd 52 fd    .R.
	mov	a,e		;; feec: 7b          {
	call	hex2a		;; feed: cd 0e fe    ...
	call	punout		;; fef0: cd 52 fd    .R.
	mov	a,e		;; fef3: 7b          {
	add	d		;; fef4: 82          .
	mov	d,a		;; fef5: 57          W
	ret			;; fef6: c9          .

trmin:	call	echin		;; fef7: cd c7 ff    ...
Lfefa:	cpi	' '		;; fefa: fe 20       .
	rz			;; fefc: c8          .
	cpi	','		;; fefd: fe 2c       .,
	rz			;; feff: c8          .
Lff00:	cpi	CR		;; ff00: fe 0d       ..
	stc			;; ff02: 37          7
	rz			;; ff03: c8          .
	cmc			;; ff04: 3f          ?
	ret			;; ff05: c9          .

puneol:	call	punimm		;; ff06: cd 4e fd    .N.
	db	CR
	call	punimm		;; ff0a: cd 4e fd    .N.
	db	LF
	ret			;; ff0e: c9          .

; program exit
pgmxit:	di			;; ff0f: f3          .
	push	h	; HL (+4)
	push	d	; DE (+6)
	push	b	; BC (+8)
	push	psw		;; ff13: f5          .
	pop	h		;; ff14: e1          .
	in	imask		;; ff15: db fc       ..
	push	psw	; IN FCH (+10)
	push	h	; PSW (+12)
	mvi	a,im1$7
	out	imask		;; ff1b: d3 fc       ..
	lhld	ramtop		;; ff1d: 2a 04 00    *..
	mvi	l,LOW resume	;; ff20: 2e d2       ..
	xchg			;; ff22: eb          .
	lxi	h,savlen+2	;; ff23: 21 0c 00    ...
	dad	sp		;; ff26: 39          9
	mvi	b,savlen/2	;; ff27: 06 05       ..
	xchg			;; ff29: eb          .
; stored saved regs in memtop...
Lff2a:	dcx	h		;; ff2a: 2b          +
	mov	m,d		;; ff2b: 72          r
	dcx	h		;; ff2c: 2b          +
	mov	m,e		;; ff2d: 73          s
	pop	d		;; ff2e: d1          .
	dcr	b		;; ff2f: 05          .
	jnz	Lff2a		;; ff30: c2 2a ff    .*.
	pop	b		;; ff33: c1          .
	dcx	b		;; ff34: 0b          .
	sphl			;; ff35: f9          .
	; find which breakpoint, if any
	lhld	ramtop		;; ff36: 2a 04 00    *..
	mvi	l,LOW brkpts	;; ff39: 2e e2       ..
	mov	a,m		;; ff3b: 7e          ~
	sub	c		;; ff3c: 91          .
	inx	h		;; ff3d: 23          #
	jnz	Lff46		;; ff3e: c2 46 ff    .F.
	mov	a,m		;; ff41: 7e          ~
	sbb	b		;; ff42: 98          .
	jz	Lff58		;; ff43: ca 58 ff    .X.
Lff46:	inx	h		;; ff46: 23          #
	inx	h		;; ff47: 23          #
	mov	a,m		;; ff48: 7e          ~
	sub	c		;; ff49: 91          .
	inx	h		;; ff4a: 23          #
	jnz	Lff53		;; ff4b: c2 53 ff    .S.
	mov	a,m		;; ff4e: 7e          ~
	sbb	b		;; ff4f: 98          .
	jz	Lff58		;; ff50: ca 58 ff    .X.
Lff53:	mvi	a,020h	; OCW2: EOI to i8259
	out	rstint	; "restore operating level" (intr)
	inx	b		;; ff57: 03          .
Lff58:	lhld	ramtop		;; ff58: 2a 04 00    *..
	mvi	l,LOW lxiHL+1	;; ff5b: 2e dc       ..
	mov	m,e		;; ff5d: 73          s
	inx	h		;; ff5e: 23          #
	mov	m,d		;; ff5f: 72          r
	mvi	l,LOW jmpPC+1	;; ff60: 2e e0       ..
	mov	m,c		;; ff62: 71          q
	inx	h		;; ff63: 23          #
	mov	m,b		;; ff64: 70          p
	push	b		;; ff65: c5          .
	call	conimm		;; ff66: cd 47 fd    .G.
	db	'#'
	pop	h		;; ff6a: e1          .
	call	adrout		;; ff6b: cd 7a fe    .z.
	; cleanup breakpoints
	lhld	ramtop		;; ff6e: 2a 04 00    *..
	mvi	l,LOW brkpts	;; ff71: 2e e2       ..
	mvi	d,2		;; ff73: 16 02       ..
Lff75:	mov	c,m		;; ff75: 4e          N
	xra	a		;; ff76: af          .
	mov	m,a		;; ff77: 77          w
	inx	h		;; ff78: 23          #
	mov	b,m		;; ff79: 46          F
	mov	m,a		;; ff7a: 77          w
	inx	h		;; ff7b: 23          #
	mov	a,c		;; ff7c: 79          y
	ora	b		;; ff7d: b0          .
	jz	Lff83		;; ff7e: ca 83 ff    ...
	mov	a,m		;; ff81: 7e          ~
	stax	b		;; ff82: 02          .
Lff83:	inx	h		;; ff83: 23          #
	dcr	d		;; ff84: 15          .
	jnz	Lff75		;; ff85: c2 75 ff    .u.
	jmp	colds		;; ff88: c3 30 f8    .0.

Lff8b:	call	rdrin		;; ff8b: cd b8 fc    ...
	jc	abort		;; ff8e: da 26 f8    .&.
	ani	07fh		;; ff91: e6 7f       ..
	ret			;; ff93: c9          .

; get PROM params T/F and X/Y/Z
;	T/F: set ramtop to data XOR mask
;	X/Y/Z: set BC according to socket type
Lff94:	in	promst		;; ff94: db f1       ..
	ora	a		;; ff96: b7          .
	jz	abort		;; ff97: ca 26 f8    .&.
	call	echin	; T/F - PROM data invert
	cpi	'T'		;; ff9d: fe 54       .T
	jnz	Lffa6		;; ff9f: c2 a6 ff    ...
	xra	a		;; ffa2: af          .
	jmp	Lffad		;; ffa3: c3 ad ff    ...

Lffa6:	cpi	'F'		;; ffa6: fe 46       .F
	jnz	abort		;; ffa8: c2 26 f8    .&.
	mvi	a,0ffh		;; ffab: 3e ff       >.
Lffad:	sta	ramtop		;; ffad: 32 04 00    2..
	call	echin	; X/Y/Z - PROM socket type
	sui	'X'		;; ffb3: d6 58       .X
	lxi	b,promsX	;; ffb5: 01 ff 00    ...
	rz			;; ffb8: c8          .
	dcr	a	; 'Y'
	lxi	b,promsY	;; ffba: 01 f0 30    ..0
	rz			;; ffbd: c8          .
	dcr	a	; 'Z'
	jnz	abort		;; ffbf: c2 26 f8    .&.
	lxi	b,promsZ	;; ffc2: 01 0f 20    ..
	ret			;; ffc5: c9          .

Lffc6:	ret			;; ffc6: c9          .

; input char, toupper, check ^C, echo
echin:	push	b		;; ffc7: c5          .
	call	conin		;; ffc8: cd 85 fc    ...
	ani	07fh		;; ffcb: e6 7f       ..
	call	toupper		;; ffcd: cd dc ff    ...
	cpi	CTRL$C		;; ffd0: fe 03       ..
	jz	abort		;; ffd2: ca 26 f8    .&.
	mov	c,a		;; ffd5: 4f          O
	call	conout		;; ffd6: cd 1d fd    ...
	mov	a,c		;; ffd9: 79          y
	pop	b		;; ffda: c1          .
	ret			;; ffdb: c9          .

toupper:
	cpi	'a'		;; ffdc: fe 61       .a
	rm			;; ffde: f8          .
	cpi	'z'+1		;; ffdf: fe 7b       .{
	rp			;; ffe1: f0          .
	ani	11011111b	;; ffe2: e6 df       ..
	ret			;; ffe4: c9          .

	db	'#',0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
	end
