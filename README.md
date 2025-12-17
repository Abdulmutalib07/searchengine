# Поисковый движок

Веб-приложение для индексации и поиска по сайтам. Реализовано на Spring Boot с использованием MySQL для хранения данных.

## Описание проекта

Поисковый движок представляет собой Spring Boot приложение, которое:
- Индексирует страницы указанных сайтов
- Выполняет морфологический анализ текста (лемматизация)
- Осуществляет поиск по проиндексированным страницам с расчетом релевантности
- Предоставляет веб-интерфейс для управления индексацией и поиска

## Технологический стек

- **Java 17+**
- **Spring Boot 2.7.1**
- **Spring Data JPA**
- **MySQL 8.0**
- **Lucene Morphology** (`org.apache.lucene.morphology`) — морфологический анализ
- **JSoup** - для парсинга HTML
- **Thymeleaf** - для шаблонов
- **Lombok** - для уменьшения boilerplate кода

## Структура проекта

```
searchengine/
├── src/main/java/searchengine/
│   ├── Application.java                 # Главный класс приложения
│   ├── config/                          # Конфигурация
│   │   ├── Site.java                    # Модель сайта из конфига
│   │   └── SitesList.java               # Список сайтов
│   ├── controllers/                     # REST контроллеры
│   │   ├── ApiController.java           # API endpoints
│   │   └── DefaultController.java       # Главная страница
│   ├── dto/                             # Data Transfer Objects
│   │   ├── indexing/                    # DTO для индексации
│   │   ├── search/                      # DTO для поиска
│   │   └── statistics/                  # DTO для статистики
│   ├── model/                           # JPA сущности
│   │   ├── Site.java                    # Сайт
│   │   ├── Page.java                    # Страница
│   │   ├── Lemma.java                   # Лемма
│   │   └── Index.java                   # Индекс (связь страница-лемма)
│   ├── repository/                      # JPA репозитории
│   └── services/                        # Бизнес-логика
│       ├── IndexingService.java         # Сервис индексации
│       ├── SearchService.java           # Сервис поиска
│       ├── StatisticsService.java       # Сервис статистики
│       └── MorphologyService.java       # Морфологический анализ
└── src/main/resources/
    ├── application.yaml                 # Конфигурация приложения
    ├── templates/index.html             # Главная страница
    └── static/                          # Статические ресурсы
```

## Требования

- Java 17 или выше
- Maven 3.6+
- MySQL 8.0+
- Доступ к интернету для индексации сайтов

## Установка и запуск

### 1. Клонирование репозитория

```bash
git clone <repository-url>
cd searchengine
```

### 2. Настройка базы данных

Создайте базу данных MySQL:

```sql
CREATE DATABASE search_engine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. Настройка конфигурации

Отредактируйте файл `src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/search_engine?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: your_username
    password: your_password

indexing-settings:
  sites:
    - url: https://www.lenta.ru
      name: Лента.ру
    - url: https://www.skillbox.ru
      name: Skillbox
  user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36
```

### 4. Сборка проекта

```bash
mvn clean package -DskipTests
```

### 5. Запуск приложения

```bash
java -jar target/SearchEngine-1.0-SNAPSHOT.jar
```

Приложение будет доступно по адресу: `http://localhost:8080`

## Использование

### Веб-интерфейс

Приложение предоставляет три основные страницы:

1. **Dashboard** - отображает статистику по проиндексированным сайтам
2. **Management** - управление индексацией (запуск/остановка) и добавление отдельных страниц
3. **Search** - поиск по проиндексированным страницам

### API Endpoints

#### Статистика
```
GET /api/statistics
```

#### Запуск индексации
```
GET /api/startIndexing
```

#### Остановка индексации
```
GET /api/stopIndexing
```

#### Индексация отдельной страницы
```
POST /api/indexPage?url=https://example.com/page
```

#### Поиск
```
GET /api/search?query=поисковый запрос&site=https://example.com&offset=0&limit=20
```

Параметры:
- `query` (обязательный) - поисковый запрос
- `site` (опциональный) - URL сайта для поиска только по нему
- `offset` (опциональный, по умолчанию 0) - смещение для пагинации
- `limit` (опциональный, по умолчанию 20) - количество результатов

## Особенности реализации

### Индексация

- Рекурсивный обход страниц сайта
- Парсинг HTML с помощью JSoup
- Извлечение текстового контента
- Морфологический анализ и лемматизация
- Сохранение в базу данных с расчетом ранга лемм

### Поиск

- Морфологический анализ поискового запроса
- Фильтрация слишком частых лемм (более 80% страниц)
- Поиск страниц, содержащих все леммы запроса
- Расчет абсолютной и относительной релевантности
- Генерация читаемых сниппетов без HTML-тегов

### Алгоритм релевантности

Релевантность рассчитывается как сумма рангов всех найденных лемм на странице, нормализованная относительно максимальной релевантности среди всех найденных страниц.

## Разработка

### Структура базы данных

- `site` - информация о сайтах
- `page` - проиндексированные страницы
- `lemma` - леммы (нормализованные формы слов)
- `search_index` - связи между страницами и леммами с рангами (поле ранга: `rank_value`)

### Стиль кода

- Соблюдение Java Code Style
- CamelCase для классов
- lowerCamelCase для методов и переменных
- Отсутствие сокращений и транслита
- Размер методов не более 30 строк
- Минимизация дублирования кода

## Лицензия

Этот проект создан в образовательных целях.

