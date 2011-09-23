#include<stdio.h>
#include<stdlib.h>
#include<windows.h>

#define BUF_SIZE 40000
TCHAR buf[BUF_SIZE+10];

int main(int argc, char* argv[]) {
  if(argc > 1) {
    //    printf("%s", SendMessage((HWND)atol(argv[1]), WM_GETTEXT, 0, 0));
    printf("got %i chars\n", SendMessage((HWND)0x04AE093E, WM_GETTEXT, BUF_SIZE, (LPARAM)buf));
    printf("%s", buf);
  }
}
