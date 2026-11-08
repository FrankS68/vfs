# My Application README

- [ ] TODO Replace or update this README with instructions relevant to your application

## Project Structure

This project has the following structure:

```
src
├── main/java
│   └── [application package]
│       ├── base
│       │   └── ui
│       │       ├── ViewToolbar.java
│       │       └── MainLayout.java
│       ├── examplefeature
│       │   ├── ui
│       │   │   └── TaskListView.java
│       │   ├── Task.java
│       │   ├── TaskRepository.java
│       │   └── TaskService.java                
│       └── Application.java     
├── main/resources
│   ├── META-INF
│   │   └── resources
│   │       └── styles.css
│   └── application.properties 
└── test/java
    └── [application package]
        └── examplefeature
           └── TaskServiceTest.java                 
```

The main entry point into the application is `Application.java`. This class contains the `main()` method that starts up 
the Spring Boot application.

The project follows a *feature-based package structure*, organizing code by *functional units* rather than traditional 
architectural layers. It includes two feature packages: `base` and `examplefeature`.

* The `base` package contains classes meant for reuse across different features, either through composition or 
  inheritance. You can use them as-is, tweak them to your needs, or remove them.
* The `examplefeature` package is an example feature package that demonstrates the structure. It represents a 
  *self-contained unit of functionality*, including UI components, business logic, data access, and an integration test.
  Once you create your own features, *you'll remove this package*.


## Starting in Development Mode

To start the application in development mode, import it into your IDE and run the `Application` class. 
You can also start the application from the command line by running: 

```bash
./mvnw
```

## Building for Production

To build the application in production mode, run:

```bash
./mvnw package
```

To build a Docker image, run:

```bash
docker build -t my-application:latest .
```

If you use commercial components, pass the license key as a build secret:

```bash
docker build --secret id=proKey,src=$HOME/.vaadin/proKey .
```

## Windows-Paket (VFS.exe)

```bash
./mvnw clean package -Pdesktop -DskipTests
```

Ergebnis ist der Ordner `target/dist/VFS/` mit `VFS.exe` und eigener Java-Laufzeit.
Auf dem Zielrechner muss kein Java installiert sein; der ganze Ordner wird kopiert.

Als Laufzeit wird die Java-Installation, mit der Maven läuft, komplett ins Paket kopiert
(auch eine JRE ohne `jmods` wie Eclipses JustJ). Eine andere Laufzeit ab Java 21 wählt
man mit `-Ddesktop.runtime-image=C:\pfad\zur\jre`.

Beim Start öffnet VFS ein Konsolenfenster mit dem Log und danach den Browser auf
`http://localhost:8080/`. Wer das Konsolenfenster schließt, beendet VFS. Ein zweiter
Start öffnet nur den Browser zur laufenden Instanz.

Die EXE nutzt das Spring-Profil `desktop` mit festen Pfaden unter `%USERPROFILE%\.vfs`:

| Was | Pfad |
|---|---|
| Datenbank | `.vfs\data\mydb.mv.db` |
| OAuth-Secrets | `.vfs\config\application.properties` (Vorlage: `config/application.properties.example`) |
| Log | `.vfs\logs\vfs.log` |

Ohne die Secrets-Datei startet VFS nicht, weil mindestens ein OAuth-Provider aktiv sein muss.
Die Callback-URLs der Provider müssen `http://localhost:8080/...` erlauben.

## Next Steps

The [Building Apps](https://vaadin.com/docs/v25/building-apps) guides contain hands-on advice for adding features to 
your application.
