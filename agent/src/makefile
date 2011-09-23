TARGET = fldagent.so

CC = gcc

# linux config
# JDK = /usr/local/share/jdk1.6.0_20
# CFLAGS=-Wall -I$(IDIR) -shared -DLINUX -I$(JDK)/include/linux -I$(JDK)/include
CFLAGS=-Wall -I$(IDIR) -shared -DLINUX -I$(JAVA_HOME)/include/linux -I$(JAVA_HOME)/include

#  windows config: compilation is ok but it hangs in runtime
# JDK = "c:/Program Files/Java/jdk1.5.0_22/"
# CFLAGS=-Wall -DGCC -DWINDOWS -I$(JDK)/include/win32 -I$(JDK)/include


# JDK = /usr/java5
# gcc  -Wall -DAIX -I/usr/java5/include -c agent.c
# gcc  -Wall -DAIX -I/usr/java5/include -c util.c
# gcc -shared -o libagent.so agent.o util.o

COMPILE = $(CC) $(CFLAGS) -c
OBJDIR = obj
SOURCES := $(wildcard *.c)
DEPS := $(wildcard *.h)
OBJFILES := $(addprefix $(OBJDIR)/, $(patsubst %.c, %.o, $(SOURCES)))

exe: $(OBJFILES)
	$(CC) $(OBJFILES) -shared -o $(TARGET)

# gcc -o $@	$(OBJFILES)

$(OBJDIR)/%.o : %.c $(DEPS)
	$(COMPILE) -o $@ $<
