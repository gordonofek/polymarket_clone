# Polymarket Clone

## Setup (IntelliJ IDEA Ultimate)

The project structure (modules, libraries, artifacts and run configurations) is committed,
so you don't need to touch **Project Structure**. The external libraries are **not** in the repo;
each person keeps them in a folder of their own and points the `LIBS` path variable at it.

### 1. Libraries folder

Put the following in one folder (any location), with **exactly** this layout:

```
<your libs folder>
├── apache-tomcat-10.0.27\
├── gson-2.14.0.jar
├── javafx-sdk-25.0.4\
│   └── lib\javafx.*.jar ...
└── jaxb-ri-4.0.5\
    └── jaxb-ri-4.0.5\
        └── mod\*.jar
```

(The JAXB zip extracts into a nested `jaxb-ri-4.0.5\jaxb-ri-4.0.5` folder – keep it that way.)

### 2. Per-machine IntelliJ settings (once, using exactly these names)

1. **`LIBS` path variable** – *Settings → Appearance & Behavior → Path Variables → +*,
   name **`LIBS`**, value = your libs folder (e.g. `C:\Users\you\Degree`).
   If it's missing when you open the project, IntelliJ shows an
   "Undefined path variables" notification that lets you set it.
2. **JDK 25** – *File → Project Structure → SDKs → + → Add JDK*, then rename it to **`ms-25`**.
3. **Tomcat** – *Settings → Build, Execution, Deployment → Application Servers → + → Tomcat Server*,
   point it at `<your libs folder>\apache-tomcat-10.0.27` and rename it to **`Tomcat 10.0.27`**.

## Running

1. Run **Tomcat Server** (builds and deploys `web:war exploded` at `http://localhost:8080/`).
2. Run **Client** (the JavaFX UI).

## Rules for keeping the setup shared

- Change project structure in IntelliJ as usual, then **commit the changed `.idea/` and `*.iml` files**.
- Never reference a jar by an absolute path. New external jars go into your libs folder and are added
  through a project library (*Project Structure → Libraries*), so IntelliJ stores them as `$LIBS$/...`.
  Tell the team so they add the same jar to their libs folder.
- New run configurations: tick **Store as project file** so they're saved under `.run/`.
- `.idea/workspace.xml` is personal and ignored – don't commit it.