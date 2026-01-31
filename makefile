# Compiler
JAVAC = javac
JAVA = java

# Directories
SRC_DIR = src
CLASSES_DIR = target

# Find all .java files
SOURCES = $(shell find $(SRC_DIR) -name "*.java")

# Convert src/path/File.java → target/path/File.class
CLASSES = $(SOURCES:$(SRC_DIR)/%.java=$(CLASSES_DIR)/%.class)

# Main class (no .java, no .class)
MAIN_CLASS = Main

.PHONY: all run clean

# Default target
all: $(CLASSES)

# Compile all classes
$(CLASSES_DIR)/%.class: $(SRC_DIR)/%.java
	@mkdir -p $(dir $@)
	$(JAVAC) -d $(CLASSES_DIR) -cp $(SRC_DIR) $<

# Run the program
run: all
	$(JAVA) -cp $(CLASSES_DIR):$(SRC_DIR) $(MAIN_CLASS)

# Clean compiled files
clean:
	rm -rf $(CLASSES_DIR)
