/*
 * e.g. For ADM3A video:
 * ./genfont -N ADM3A -b 0 -n 128 -c 8 -w 5 -p 2,1 -g -h 8 -a 8 adm3a_cg.bin
 * ./genfont -N ADM3A -g adm3a_cg.bin >ADM3A.sfd
 */
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdlib.h>
#include <pwd.h>
#include <time.h>
#include <string.h>
#include <unistd.h>

unsigned char *buf;
int base = 0; // start address in ROM
int num = 128; // number of characters (in ROM)
int bpc = 8; // bytes per character (in ROM)
int inv = 0; // invert bytes from ROM
int rev = 0; // doing reverse video characters
int itl = 0; // interlace mode
int wid = 5;
int padl = 1;
int padr = 1;
int hei = 9;
int asc = 9;
int dsc = 0; // computed (hei - asc)

// These numbers are divided by 10 to get font coords.
// TODO: use float/double instead?
int dot_width = 1024;
int trace_height = 1024;
int trace_spacing = 2048; // non-interlaced mode

/* "DOTS" will render as round dots, like a dot-matrix printer */
// TODO: this must be reworked...
#undef DOTS

#define TRACE_HEIGHT 1024
#define DOT_WIDTH 1024

#ifdef DOTS

// Use dot-line ratio 1:1, causing a vertically compress stature.
#define TRACE_SPACING TRACE_HEIGHT

#define CHAR_WIDTH (DOT_WIDTH * 8)
#define CHAR_HEIGHT (TRACE_SPACING * 8)
#define CHAR_BASELINE (TRACE_SPACING * 2)

#else

// For H19, Use dot-line ratio 1:2 since 1:1.92 gets messy.
// Non-interlaced really is 1:2.

#endif

void do_row_dots(int i, int b) {
#if 0
	// tan(28.9) ~= 0.552 ...
	static int dd = (DOT_WIDTH / 2);
	static int ds = (((DOT_WIDTH / 2) * 552) + 500) / 1000;

	int x, y;

	x = 0;
	y = i * TRACE_HEIGHT - CHAR_BASELINE + (TRACE_HEIGHT / 2);
	while (b) {
		if ((b & 0x80) != 0) {
			printf("%d %d m 0\n", x, y);
			printf(" %d %d %d %d %d %d c 0\n",
				x, y + ds,
				x + dd - ds, y + dd,
				x + dd, y + dd);
			printf(" %d %d %d %d %d %d c 0\n",
				x + dd + ds, y + dd,
				x + DOT_WIDTH, y - ds,
				x + DOT_WIDTH, y);
			printf(" %d %d %d %d %d %d c 0\n",
				x + DOT_WIDTH, y - ds,
				x + dd + ds, y - dd,
				x + dd, y - dd);
			printf(" %d %d %d %d %d %d c 0\n",
				x + dd - ds, y - dd,
				x, y - ds,
				x, y);
		}
		x += DOT_WIDTH;
		b <<= 1;
	}
#endif
}

// 'i' is row number, starting at bottom with "0".
void do_row(int i, int b, int w) {
	int x, y;
	int m;
	int xs;
	int xt, yt, yyt;

	x = xs = 0;
	int d = (itl == 1 ? dsc * 2 : dsc);
	y = ((i - d) * trace_spacing);	// 10X !
	m = 0;
	int mk = ((1 << w) - 1);
	int mm = (1 << (w - 1));
	while ((b & mk) || m) {
		if ((b & mm) != m) {
			xt = (x + 5) / 10;
			yt = (y + 5) / 10;
			yyt = (y + trace_height + 5) / 10;
			if (itl != 0) {
				--yyt;
			}
			if (m == 0) {
				// start glyph - draw clockwise...
				xs = xt;
				printf("%d %d m 1\n", xt, yt);
				printf(" %d %d l 1\n", xt, yyt);
			} else {
				// finish glyph
				printf(" %d %d l 1\n", xt, yyt);
				printf(" %d %d l 1\n", xt, yt);
				printf(" %d %d l 1\n", xs, yt);
			}
			m = (b & mm);
		}
		x += dot_width;
		b <<= 1;
	}
}

void do_char(int c, int fc, int ul) {
	int i;
	int b;
	int a = c * bpc + base;
	static int cn = 0;

	printf("StartChar: uni%04X\n", fc);
	printf("Encoding: %d %d %d\n", fc, fc, cn++); // what is 3rd number?
	printf("Width: %d\n", (dot_width * (wid + padl + padr)) / 10);
	printf("VWidth: 0\n");
	printf("Flags: HW\n");
	printf("LayerCount: 2\n");
	printf("Fore\n");
	printf("SplineSet\n");
	int r;
	// arbitrary row counter... does not imply array index
	for (r = 0; r < hei; ++r) {
		// char gen ROMs start with top row,
		// SFD/TTF convention is starting at bottom.
		int i = hei - r - 1;	// 8,7,6,5,4,3,2,1,0
		int w = wid + padl + padr;
		if (i >= bpc) {
			b = 0;
		} else {
			b = buf[a + i];
		}
		if (inv) {
			b = ~b;
		}
		if (ul && r == 0) {
			b = (1 << w) - 1;
		}
		b <<= padr;
		if (rev) {
			b = ~b;
		}
#ifndef DOTS
		if (itl == 1) {
			do_row(r * 2, b, w);
			do_row(r * 2 + 1, b, w);
		} else {
			do_row(r, b, w);
		}
#else
		do_row_dots(hei - i - 1, b, w);
#endif
	}
	printf("EndSplineSet\n");
	printf("EndChar\n\n");
}

static void preamble(int ascent, int descent, char *name, char *arg) {
	struct passwd *pw = getpwuid(getuid());
	time_t t = time(NULL);
	struct tm *tm = localtime(&t);
	char *user = pw->pw_gecos;
	if (*user == 0 || *user == ' ') {
		user = pw->pw_name;
	} else {
		int n = strlen(user);
		while (n > 0 && user[n - 1] == ',') {
			user[--n] = 0;
		}
	}
	if (itl == 1) {
		ascent *= 2;
		descent *= 2;
	}
	printf(	"SplineFontDB: 3.0\n"
		"FontName: %s\n"
		"FullName: %s\n"
		"FamilyName: %s\n"
		"Weight: Medium\n"
		"Copyright: Created by %s with genfont %s\n"
		"UComments: \"%04d-%d-%d: Created.\" \n"
		"Version: 001.000\n"
		"ItalicAngle: 0\n"
		"UnderlinePosition: -100\n"
		"UnderlineWidth: 50\n"
		"Ascent: %d\n"
		"Descent: %d\n"
		"LayerCount: 2\n"
		"Layer: 0 0 \"Back\"  1\n"
		"Layer: 1 0 \"Fore\"  0\n"
		"XUID: [1021 590 %ld 919824]\n"
		"FSType: 0\n"
		"OS2Version: 0\n"
		"OS2_WeightWidthSlopeOnly: 0\n"
		"OS2_UseTypoMetrics: 1\n"
		"CreationTime: %ld\n"
		"ModificationTime: %ld\n"
		"OS2TypoAscent: 0\n"
		"OS2TypoAOffset: 1\n"
		"OS2TypoDescent: 0\n"
		"OS2TypoDOffset: 1\n"
		"OS2TypoLinegap: 90\n"
		"OS2WinAscent: 0\n"
		"OS2WinAOffset: 1\n"
		"OS2WinDescent: 0\n"
		"OS2WinDOffset: 1\n"
		"HheadAscent: 0\n"
		"HheadAOffset: 1\n"
		"HheadDescent: 0\n"
		"HheadDOffset: 1\n"
		"OS2Vendor: 'PfEd'\n"
		"DEI: 91125\n"
		"Encoding: Custom\n"
		"UnicodeInterp: none\n"
		"NameList: Adobe Glyph List\n"
		"DisplaySize: -24\n"
		"AntiAlias: 1\n"
		"FitToEm: 1\n"
		"WinInfo: 16 16 15\n",
		name, name, name,
		user, arg,
		tm->tm_year + 1900, tm->tm_mon + 1, tm->tm_mday,
		ascent, descent,
		t, t, t);
}

static void genchars(int rvoff, int uloff) {
	int c;
	for (c = 0; c < num; ++c) {
		int fc = c | uloff;
		if (fc < 32) {
			fc |= 0x100;
		}
		do_char(c, fc, uloff > 0);
	}
	if (rvoff <= 0) {
		return;
	}
	rev = 1;
	for (c = 0; c < num; ++c) {
		int fc = c | rvoff | uloff;
		do_char(c, fc, uloff > 0);
	}
	rev = 0;
}

int main(int argc, char **argv) {
	int c;
	int x, y;
	char *e;
	int rvoff = 0x200; // legacy (H19 128) char layout is 0x80
	int uloff = 0;
	int fd = -1;
	struct stat stb;
	char *name = "Untitled";

	extern int optind;
	extern char *optarg;

	while ((x = getopt(argc, argv, "a:b:c:gh:il:n:N:p:rR:u:w:W:")) != EOF) {
		switch(x) {
		case 'a':
			// Ascent, <= height
			asc = strtoul(optarg, NULL, 0);
			break;
		case 'b':
			base = strtoul(optarg, NULL, 0);
			break;
		case 'c':
			bpc = strtoul(optarg, NULL, 0);
			break;
		case 'g':
			// TODO: confirm num <= 128?
			rvoff = 0x80;
			break;
		case 'h': // height, <= bpc
			hei = strtoul(optarg, NULL, 0);
			break;
		case 'i':
			++inv;
			break;
		case 'l':
			// TODO: parse mnemonic
			itl = strtoul(optarg, NULL, 0);
			break;
		case 'n':
			num = strtoul(optarg, NULL, 0);
			break;
		case 'N':
			name = optarg;
			break;
		case 'p':
			padl = strtoul(optarg, &e, 0);
			if (e == optarg || (*e && *e != ',')) {
				fprintf(stderr, "Invalid -p padding\n");
				goto error;
			}
			if (*e == ',') {
				padr = strtoul(e + 1, &e, 0);
				if (*e) {
					fprintf(stderr, "Invalid -p padding\n");
					goto error;
				}
			}
			break;
		case 'r':
			rvoff = 0;
			break;
		case 'R':
			rvoff = strtoul(optarg, NULL, 0);
			break;
		case 'u':
			uloff = strtoul(optarg, NULL, 0);
			break;
		case 'w': // width, <= 8
			wid = strtoul(optarg, NULL, 0);
			break;
		case 'W': // width, <= 8
			{
			double d = strtod(optarg, NULL);
			if (d > 0) {
				dot_width = (d * dot_width + 0.5);
			}
			break;
			}
		}
	}
	x = optind;

	if (x < argc) {
		fd = open(argv[x], O_RDONLY);
		if (fd >= 0) {
			fstat(fd, &stb);
		}
	}
	if (fd < 0) {
error:
		fprintf(stderr,
			"Usage: %s [options] <rom-image>\n"
			"Options:\n"
			"         -N name  Name to use in output SFD data\n"
			"         -b base  Address of char 0 in ROM (0x000)\n"
			"         -n num   Num chars (128)\n"
			"         -c bpc   Bytes per chr (16)\n"
			"         -i       Invert ROM bits\n"
			"         -a asc   Ascent, lines above baseline (8)\n"
			"         -h hgt   Height of displayed chr, lines (10)\n"
			"         -w wid   Width of displayed chr, dots (8)\n"
			"         -p l,r   Padding l(eft) and r(ight) of wid (0,0)\n"
			"         -l intl  Interlace mode (0):\n"
			"                  0 = Non-interlaced\n"
			"                  1 = Interlace sync\n"
			"                  2 = Interlace sync+video\n"
			"         -R off   Use off for reverse video chars (def: 0x200)\n"
			"         -u off   Generate underlined chars at off\n"
			"         -r       Do not produce rev vid chrs\n"
			"         -g       Use legacy 0x80 offset for rev vid chrs (0x200)\n"
			, argv[0]);
		exit(1);
	}
	buf = malloc(stb.st_size);
	read(fd, buf, stb.st_size);
	close(fd);

	if (itl != 0) {
		trace_spacing /= 2;
	}
	dsc = hei - asc;

	preamble((trace_spacing * asc + 5) / 10, ((trace_spacing * dsc + 5) / 10),
		name, argv[x]);

	int max = 0x100 + 32; // assume at least 0x100-0x11f
	int cnt = num;
	if (rvoff > 0) {
		cnt *= 2;
		if (rvoff + num > max) {
			max = rvoff + num;
		}
	}
	printf("\nBeginChars: %d %d\n\n", max, cnt);

	genchars(rvoff, 0);
	if (uloff > 0) {
		genchars(rvoff, uloff);
	}
	printf("\nEndChars\n"
		"EndSplineFont\n");

	return 0;
}
