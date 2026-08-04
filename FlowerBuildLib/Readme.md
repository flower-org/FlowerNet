# FlowerBuildLib

`FlowerBuildLib` — набор Gradle convention plugins с общими настройками проектов Flower.

## Подключение

Добавьте included build в начало `settings.gradle` проекта:

```groovy
pluginManagement {
    includeBuild('FlowerBuildLib')
}
```

Путь в `includeBuild` должен указывать на локальную директорию `FlowerBuildLib` относительно
`settings.gradle` подключаемого проекта.

Затем подключите нужные плагины в `build.gradle` модуля:

```groovy
plugins {
    id 'application'
    id 'com.flower.java-base'
    id 'com.flower.errorprone'
    id 'com.flower.javafx'
    id 'com.flower.jpackage'
}
```

Плагины можно использовать независимо. `java-base` рекомендуется указывать явно, чтобы по
`build.gradle` сразу было видно, какие базовые настройки получает модуль.

## Доступные плагины

### `com.flower.java-base`

Настраивает:

- Java toolchain 17;
- кодировку компиляции UTF-8;
- сохранение имён параметров через `-parameters`;
- JUnit Platform для задач `Test`;
- репозиторий Maven Central.

### `com.flower.errorprone`

Подключает Error Prone и NullAway, исключает сгенерированные исходники из основной проверки.
Необходимо указать пакеты, код которых анализирует NullAway:

```groovy
flowerErrorProne {
    annotatedPackages = ['com.example.application']
}
```

Можно перечислить несколько пакетов:

```groovy
flowerErrorProne {
    annotatedPackages = [
            'com.example.core',
            'com.example.ui'
    ]
}
```

Без `annotatedPackages` конфигурация проекта завершится ошибкой.

### `com.flower.javafx`

Подключает JavaFX 21.0.2 со стандартным набором модулей:

```text
javafx.controls
javafx.fxml
javafx.graphics
javafx.media
```

При необходимости список можно переопределить в обычном блоке JavaFX:

```groovy
javafx {
    modules = ['javafx.controls', 'javafx.fxml']
}
```

JavaFX native-зависимости выбираются для ОС, на которой выполняется Gradle. Если подключаемый
модуль сам транзитивно добавляет JavaFX сразу для нескольких платформ, исключите эти зависимости
и оставьте `com.flower.javafx` единственным источником JavaFX:

```groovy
implementation(project(':SomeJavaFxModule')) {
    exclude group: 'org.openjfx'
}
```

### `com.flower.jpackage`

Подключает `application`, Beryx Runtime Plugin и базовые параметры runtime image. Специфичные для
приложения настройки задаются в стандартных блоках:

```groovy
application {
    mainModule = 'com.example.application'
    mainClass = 'com.example.application.Main'
}

runtime {
    jpackage {
        imageName = 'ExampleApp'
        installerName = 'ExampleApp'
        appVersion = '1.0.0'
    }
}
```

Дополнительные параметры Beryx Runtime/JPackage также указываются в блоке `runtime`.

## Проверка FlowerBuildLib

Из корня основного проекта:

```bash
./gradlew -p FlowerBuildLib test
```

В Windows:

```powershell
.\gradlew.bat -p FlowerBuildLib test
```
