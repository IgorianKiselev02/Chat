# Talk

В рамках задания необходимо реализовать систему простых чатов.

Система включает
 * сервис реестра пользователей (HTTP REST API)
 * клиентское приложения работающие по одному из нескольких протоколов: HTTP, UDP, WEBSOCKET
  
## Сервер для регистрации (подпроект `registry`)

Сервис должен поддерживает следующий интерфейс:

 *  Регистрация пользователя 
    ```
    POST /v1/users
    { "user" : "<name>", "address" : { "protocol": "<name>", host": "<host or ip>", "port":"<port>" } }
    ```
    В случае успеха ответ `200 OK`
    ```
    { "status" : "ok" } 
    ```
    Если пользователь уже есть - `409 Conflict`
    Имя пользователя не должно быть пустым, должно состоять из [a-zA-Z0-9-_.]. В случае неверного имени - `400 Bad Request` 

 *  Обновление пользователя 
    ```
    PUT /v1/users/{user}
    { "host": "<host or ip>", "port": "<port>" }
    ```
    В случае успеха ответ `200 OK`
    ```
    { "status" : "ok" }     
    ```
    Если пользователя нет - создать как при регистрации
    Имя пользователя не должно быть пустым, должно состоять из [a-zA-Z0-9-_.]. В случае неверного имени - `400 Bad Request` 

 *  Получение списка пользователей 
    ```
    GET /v1/users/
    ```
    В случае успеха ответ `200 OK`. Пример:
    ```
    {
      "user1" : {
        "protocol: "HTTP"
        "host" : "127.0.0.1",
        "port" : 8083
      },
      "user2" : {
        "protocol": "UDP"
        "host" : "myhost.example.com",
        "port" : 3002
      },
      "user3" : {
        "protocol": "WEBSOCKET"
        "host" : "192.168.0.1",
        "port" : 8080
      } 
    ```

 *  Удаление пользователя 
    ```
    DELETE /v1/users/{user}
    ```
    В случае успеха ответ `200 OK`
    ```
    { "status" : "ok" }     
    ```
## Клиентское приложение (подпроект `client`)

Приложение реализует command line чат со следующими командами: 

* `:update` - Обновление списка пользователей, вывод пользователей и их адресов.
              Сообщение об ошибке, если реестр недоступен. 
              Если выбранный пользователь был удалён - сбросить пользователя для отправки сообщений.
* `:user <user>` - Выбор пользователя для отправки сообщений. 
                   Сообщение об ошибке если пользователя нет или его протокол пока не поддерживается.
* `<text>` - Отправка сообщения выбранному пользователю. 
             Сообщение об ошибке если пользователь не выбран.
             Сообщение об ошибке, если отправка сообщения не удалась.
* `:exit` - Выход. Перед выходом удаляем клиента из реестра. 
  
Клиент принимает сообщение по протоколу нескольким протоколам. 
Порт по умолчанию - `8080`, путь  - `/v1/message`

Сообщение передаётся в виде json: `{ "user" : "<имя отправителя>", "text": "<текст сообщение>" }`
При старте клиента доступны следующие опции:

* `--name` - имя клиента
* `--registry` - базовый URL реестра, например `http://127.0.01:8088`
* `--host` - hostname или ip, на котором клиент будет слушать сообщения
* `--port` - порт (Int), на котором клиент будет слушать сообщения
* `--public-url` - URL по которому клиент доступен извне (может отличаться от http://<host>:<port>, если используется прокси, например ngrok).
 
При старте клиент регистрируется в реестре со `host` и `port` из `public-url` и выполняет команду `:update`. 

# Talk Chat with Database support

В рамках задания необходимо добавить к системе простых чатов из задания `talk-chat` 
сохранения состояния реестра пользователей в реляционной базе данных.

**В данном репозитории представлена простая реализация `talk-chat` для тех команд, 
которые не смогли реализовать её в прошлом семестре. Остальные команды, должны 
перенести свой код в данный репозиторий из репозитория команды, 
соответствующего заданию `talk-chat`.**  

## Требование к поддержке сохранения в базе данных для `registry`

1. Поддержать сохранение состояния в памяти, либо в базе данных - выделить общий интерфейс хранилища. 
   Способ хранения должен определяться настройками в конфигурационном файле (по умолчанию `registry/resources/application.conf`).  
2. В качестве базы данных использовать встроенную реляционную базу данных. Например, SQLite или H2.
   Путь к базе данных и параметры подключения должны определяться настройками в конфигурационном файле (по умолчанию `registry/resources/application.conf`).
3. Поддержать создание новой базы данных при первом запуске.

Можно/нужно использовать сторонние библиотеки. Например, [Exposed](https://github.com/JetBrains/Exposed) или [SQLDelight](https://github.com/cashapp/sqldelight) 

### Тесты

Необходимо реализовать тесты для всех операций работы с хранилищем. 


