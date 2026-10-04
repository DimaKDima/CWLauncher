package ru.cw.launcher.ui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Тексты лаунчера. Выбранный язык меняет интерфейс, не только Minecraft. */
public final class Lang {

    private static volatile String code = "ru";
    private static final Map<String, Map<String, String>> ALL = new HashMap<>();

    static {
        put("ru",
                "site", "Сайт сервера", "profile", "Профиль", "min", "Свернуть", "close", "Закрыть",
                "support", "Тех. Поддержка", "updates", "Обновления", "update", "Обновление",
                "folder", "Папка\nMinecraft", "mods", "Моды", "skin", "Скин", "version", "Выбор версии",
                "settings", "Настройки", "more", "Подробнее →", "cancel", "Отмена", "close_game", "Закрыть",
                "launch", "Запустить", "install", "Установить", "update_btn", "Обновить",
                "check", "Проверить сборку", "checking", "Проверка…", "starting", "Запускается",
                "running", "Запущен", "update_mods", "Обновить моды", "update_mods_h", "Доступно обновление модов",
                "update_mc", "Обновить Minecraft", "update_mc_h", "Нужно обновить файлы Minecraft",
                "go", "Запуск",
                "srv_down", "Статус сервера недоступен", "srv_on", "Сервер онлайн",
                "srv_off", "Сервер не отвечает", "no_addr", "адрес не задан", "players", "Игроков: ",
                "news", "Новости", "news_down", "Новости недоступны",
                "foot_none", "Сервер: Нет данных", "foot_off", "Сервер: Не отвечает",
                "tab_main", "Основные", "tab_game", "Игра", "tab_mods", "Моды", "tab_look", "Внешний вид",
                "tab_extra", "Дополнительно", "save", "Сохранить изменения", "reset", "Сбросить настройки",
                "lang", "Язык интерфейса", "lang_hint", "Язык лаунчера и Minecraft. После сохранения лаунчер переключается сразу.",
                "lang_pick", "Выберите язык", "lang_sys", "Как в Windows",
                "ram", "Оперативная память", "ram_hint", "Ползунок идёт до всей памяти компьютера.",
                "ram_of", "из", "dim", "Прозрачность", "dim_hint", "Затемнение фона в окнах, кроме главного меню. Двигайте ползунок — фон этого окна меняется сразу.",
                "theme", "Тема лаунчера", "theme_hint", "Цветовая схема. Сейчас тёмная синяя.",
                "gb", "ГБ", "news_cache", "Источник не отвечает. Показан кэш, если он был.",
                "news_none", "Источник новостей не настроен.",
                "status_err", "Проблемы сборки", "status_info", "Информация",
                "status_work", "Процесс установки", "status_build", "Состояние сборки",
                "update_avail", "Доступно обновление", "fabric_missing", "Fabric не установлен",
                "mods_missing", "Моды не установлены", "latest", "Последние обновления",
                "player", "Игрок", "foot_mods", "Моды", "mods_fresh", "Актуальны",
                "mods_need", "не нужны", "mods_attn", "Требуют внимания",
                "mods_na", "Не установлены", "mods_upd", "Есть обновление",
                "build_prefix", "Сборка: ", "server_ok", "Сервер доступен",
                "every_day", "Каждый день", "every_start", "При запуске", "every_manual", "Вручную",
                "style_std", "Стандартный", "style_compact", "Компактный",
                "band_none", "Без ограничений", "mbit", "Мбит/с",
                "menu_behavior", "Показывать главное меню", "blue", "Синяя",
                "nofull", "Отключить полноэкранную оптимизацию",
                "hipri", "Высокий приоритет процесса",
                "nort", "Отключить оптимизацию в реальном времени",
                "fullscreen", "Полный экран", "blur", "Размытие фона", "anim", "Анимации",
                "fade", "Плавные переходы", "fps", "Показать FPS",
                "disk", "Проверять свободное место на диске",
                "proxy", "Использовать системный прокси",
                "offline", "Разрешить запуск без интернета",
                "dev", "Режим разработчика",
                "dev_hint", "Отдельные папки игры и сколько угодно окон. Со второго окна можно задать временный ник.",
                "dev_title", "Откуда запустить",
                "dev_pick", "Папка этой версии Minecraft: свои моды, миры, конфиги и загрузки. Версия и сервер те же.",
                "dev_main", "Основная",
                "dev_create", "Создать",
                "dev_nick", "Временный ник",
                "dev_nick_h", "Можно зайти на сервер другим ником. Ник только для этого окна и не сохраняется. Пропустить — тот же аккаунт.",
                "dev_skip", "Пропустить",
                "dev_go", "Запустить",
                "dev_bad_nick", "Ник: 3–16 символов, латиница, цифры и _",
                "dev_bad_name", "Введите имя папки",
                "verbose", "Подробные логи",
                "pick_bg", "Выбрать фон", "browse", "Обзор", "edit", "Редактировать",
                "open_mods", "Открыть управление модами", "clear", "Очистить кэш",
                "open_dl", "Открыть папку загрузок", "cache_size", "Размер: ",
                "limit_name", "Ограничение скорости интернета",
                "limit_hint", "Включённый лимит режет все загрузки лаунчера.",
                "autostart", "Автозапуск Windows",
                "autostart_h", "Запускать CWLauncher при включении компьютера.",
                "autoupdate", "Автообновление лаунчера",
                "autoupdate_h", "Лаунчер будет проверять обновление при запуске.",
                "behavior", "Поведение при запуске",
                "behavior_h", "После запуска Minecraft лаунчер остаётся открытым.",
                "notes", "Уведомления",
                "notes_h", "Показывать уведомления о важных событиях: обновления и ошибки.",
                "time_show", "Показ времени",
                "time_sec", "Секунды",
                "time_min", "Минуты",
                "time_hour", "Часы",
                "time_day", "Дни",
                "time_week", "Недели",
                "upd_btn", "Обновить лаунчер",
                "upd_toast", "Доступно обновление лаунчера ",
                "upd_now", ". Сейчас установлена ",
                "check_updates", "Проверять обновления", "on_launch", "Что делать при запуске",
                "java", "Java",
                "java_h", "Своя Java вместо автоматически найденной. Нужна Java",
                "jvm", "Дополнительные JVM-параметры",
                "jvm_h", "Неверные аргументы могут помешать запуску. Память -Xmx отсюда не берётся.",
                "leader", "Режим «весь интернет на себя»",
                "leader_h", "Лаунчер качает в 16 потоков, повторяет обрыв и поднимает свой приоритет.",
                "gameopt", "Игровые параметры",
                "gameopt_h", "Экран и дополнительные флаги запуска.",
                "res", "Разрешение", "fps_cap", "Лимит FPS, 0 — без ограничения",
                "modsup", "Автообновление модов",
                "modsup_h", "Если в архиве модов версия новее установленной, кнопка запуска предложит обновить моды.",
                "verify", "Проверка сборки",
                "verify_h", "Проверять целостность файлов модов. Если выключить, ошибка из-за расхождения файлов не появится.",
                "modmgr", "Управление модами",
                "modmgr_h", "Открыть папку модов текущей версии и положить туда свои файлы.",
                "sources", "Источники модов",
                "sources_h", "Сборка Common World скачивается одним архивом modsCWL.zip.",
                "backups", "Резервные копии",
                "backups_h", "Перед установкой новых модов предыдущие файлы сохраняются.",
                "copies", "Количество копий",
                "bg", "Фон",
                "bg_h", "Картинка главного меню. Новая добавляется в папку и включается, только когда её выбирают в списке.",
                "look_theme", "Тема", "look_theme_h", "Цветовая схема интерфейса.", "color", "Цвет",
                "effects", "Эффекты", "effects_h", "Какие украшения интерфейса запоминать.",
                "uistyle", "Стиль элементов",
                "uistyle_h", "Плотность подписей в этом окне не меняет кнопки главного меню.",
                "cache", "Кэш",
                "cache_h", "Очистка временных файлов. Миры, аккаунты и настройки не трогаются.",
                "logs", "Логи", "logs_h", "Просмотр логов лаунчера и игры.",
                "extra_opts", "Дополнительные параметры", "extra_h", "Мелкие переключатели лаунчера.",
                "downloads", "Загрузки",
                "downloads_h", "Папка, куда лаунчер складывает временные загрузки.",
                "diag", "Диагностика", "diag_h", "Файл для поддержки: без токенов и паролей.",
                "reset_card", "Сброс настроек",
                "reset_h", "Вернуть поля этой формы к обычным значениям. Игру и аккаунты это не удаляет.",
                "reset_short", "Сбросить", "wipe_ver", "Удалить версии игры", "wipe_all", "Удалить лаунчер");
        put("en",
                "site", "Server site", "profile", "Profile", "min", "Minimize", "close", "Close",
                "support", "Support", "updates", "Updates", "update", "Update",
                "folder", "Minecraft\nfolder", "mods", "Mods", "skin", "Skin", "version", "Choose version",
                "settings", "Settings", "more", "Details →", "cancel", "Cancel", "close_game", "Close",
                "launch", "Play", "install", "Install", "update_btn", "Update",
                "check", "Check build", "checking", "Checking…", "starting", "Starting",
                "running", "Running", "update_mods", "Update mods", "update_mods_h", "Mods update available",
                "update_mc", "Update Minecraft", "update_mc_h", "Minecraft files need an update",
                "go", "Launch",
                "srv_down", "Server status unavailable", "srv_on", "Server online",
                "srv_off", "Server not responding", "no_addr", "address not set", "players", "Players: ",
                "news", "News", "news_down", "News unavailable",
                "foot_none", "Server: No data", "foot_off", "Server: Not responding",
                "tab_main", "General", "tab_game", "Game", "tab_mods", "Mods", "tab_look", "Appearance",
                "tab_extra", "Advanced", "save", "Save changes", "reset", "Reset settings",
                "lang", "Interface language", "lang_hint", "Language of the launcher and Minecraft. It switches as soon as you save.",
                "lang_pick", "Choose language", "lang_sys", "Match Windows",
                "ram", "Memory", "ram_hint", "The slider goes up to all the RAM in this computer.",
                "ram_of", "of", "dim", "Transparency", "dim_hint", "Darkens the background in every window except the main menu. Move the slider and this window changes at once.",
                "theme", "Launcher theme", "theme_hint", "Color scheme. It stays dark blue.",
                "gb", "GB", "news_cache", "The source is not responding. A cached copy is shown when one exists.",
                "news_none", "The news source is not configured.",
                "status_err", "Build problems", "status_info", "Information",
                "status_work", "Installation", "status_build", "Build status",
                "update_avail", "Update available", "fabric_missing", "Fabric is not installed",
                "mods_missing", "Mods are not installed", "latest", "Latest updates",
                "player", "Player", "foot_mods", "Mods", "mods_fresh", "Up to date",
                "mods_need", "not needed", "mods_attn", "Need attention",
                "mods_na", "Not installed", "mods_upd", "Update available",
                "build_prefix", "Build: ", "server_ok", "Server reachable",
                "every_day", "Every day", "every_start", "On startup", "every_manual", "Manual",
                "style_std", "Standard", "style_compact", "Compact",
                "band_none", "Unlimited", "mbit", "Mbit/s",
                "menu_behavior", "Show the main menu", "blue", "Blue",
                "nofull", "Disable fullscreen optimization",
                "hipri", "High process priority",
                "nort", "Disable real-time optimization",
                "fullscreen", "Fullscreen", "blur", "Blur the background", "anim", "Animations",
                "fade", "Smooth transitions", "fps", "Show FPS",
                "disk", "Check free disk space",
                "proxy", "Use the system proxy",
                "offline", "Allow launch without internet",
                "dev", "Developer mode",
                "dev_hint", "Separate game folders and as many windows as you need. From the second window you can set a temporary nick.",
                "dev_title", "Where to launch",
                "dev_pick", "A folder of this Minecraft version: its own mods, worlds, configs and downloads. Same version and server.",
                "dev_main", "Main",
                "dev_create", "Create",
                "dev_nick", "Temporary nick",
                "dev_nick_h", "Join the server with another nick. It is used only for this window and is not saved. Skip keeps the current account.",
                "dev_skip", "Skip",
                "dev_go", "Launch",
                "dev_bad_nick", "Nick: 3–16 characters, letters, digits and _",
                "dev_bad_name", "Enter a folder name",
                "verbose", "Verbose logs",
                "pick_bg", "Choose background", "browse", "Browse", "edit", "Edit",
                "open_mods", "Open the mods folder", "clear", "Clear cache",
                "open_dl", "Open the downloads folder", "cache_size", "Size: ",
                "limit_name", "Limit download speed",
                "limit_hint", "The limit applies to every launcher download.",
                "autostart", "Start with Windows",
                "autostart_h", "Start CWLauncher when the computer turns on.",
                "autoupdate", "Launcher auto-update",
                "autoupdate_h", "The launcher checks for an update when it starts.",
                "behavior", "Launch behavior",
                "behavior_h", "The launcher stays open after Minecraft starts.",
                "notes", "Notifications",
                "notes_h", "Show notifications about updates and errors.",
                "time_show", "Time display",
                "time_sec", "Seconds",
                "time_min", "Minutes",
                "time_hour", "Hours",
                "time_day", "Days",
                "time_week", "Weeks",
                "upd_btn", "Update launcher",
                "upd_toast", "Launcher update available ",
                "upd_now", ". Installed now ",
                "check_updates", "Check for updates", "on_launch", "What to do on launch",
                "java", "Java",
                "java_h", "Your own Java instead of the one found automatically. Required Java",
                "jvm", "Extra JVM arguments",
                "jvm_h", "Bad arguments can stop the game from starting. -Xmx here is ignored.",
                "leader", "Use the whole connection",
                "leader_h", "The launcher downloads in 16 threads, retries drops and raises its own priority.",
                "gameopt", "Game options",
                "gameopt_h", "Screen and extra launch flags.",
                "res", "Resolution", "fps_cap", "FPS limit, 0 means unlimited",
                "modsup", "Mod auto-update",
                "modsup_h", "If the mods archive is newer than the installed pack, the launch button offers to update the mods.",
                "verify", "Verify the build",
                "verify_h", "Check mod files. Turn this off to skip the mismatch error.",
                "modmgr", "Manage mods",
                "modmgr_h", "Open this version's mods folder and put your own files there.",
                "sources", "Mod sources",
                "sources_h", "The Common World pack is downloaded as one modsCWL.zip archive.",
                "backups", "Backups",
                "backups_h", "Previous mod files are kept before a new install.",
                "copies", "Number of copies",
                "bg", "Background",
                "bg_h", "Main menu picture. A new image is added to the folder and applied only when you pick it in the list.",
                "look_theme", "Theme", "look_theme_h", "Interface color scheme.", "color", "Color",
                "effects", "Effects", "effects_h", "Which interface extras to keep.",
                "uistyle", "Element style",
                "uistyle_h", "Spacing in this window does not change the main menu buttons.",
                "cache", "Cache",
                "cache_h", "Clears temporary files. Worlds, accounts and settings stay.",
                "logs", "Logs", "logs_h", "Launcher and game logs.",
                "extra_opts", "Extra options", "extra_h", "Small launcher switches.",
                "downloads", "Downloads",
                "downloads_h", "Folder where the launcher keeps temporary downloads.",
                "diag", "Diagnostics", "diag_h", "A support file without tokens or passwords.",
                "reset_card", "Reset settings",
                "reset_h", "Restore this form. The game and accounts are not deleted.",
                "reset_short", "Reset", "wipe_ver", "Delete game versions", "wipe_all", "Uninstall launcher");
        same("uk", "en", "site", "Сайт сервера", "profile", "Профіль", "min", "Згорнути", "close", "Закрити",
                "support", "Підтримка", "updates", "Оновлення", "update", "Оновлення",
                "folder", "Папка\nMinecraft", "mods", "Моди", "skin", "Скін", "version", "Вибір версії",
                "settings", "Налаштування", "more", "Детальніше →", "cancel", "Скасувати", "close_game", "Закрити",
                "launch", "Запустити", "install", "Встановити", "update_btn", "Оновити",
                "check", "Перевірити збірку", "checking", "Перевірка…", "starting", "Запускається",
                "running", "Запущено", "update_mods", "Оновити моди", "go", "Запуск",
                "srv_down", "Статус сервера недоступний", "srv_on", "Сервер онлайн",
                "srv_off", "Сервер не відповідає", "players", "Гравців: ",
                "news", "Новини", "news_down", "Новини недоступні",
                "tab_main", "Основне", "tab_game", "Гра", "tab_mods", "Моди", "tab_look", "Вигляд",
                "tab_extra", "Додатково", "save", "Зберегти зміни", "reset", "Скинути налаштування",
                "lang", "Мова інтерфейсу", "lang_pick", "Оберіть мову", "lang_sys", "Як у Windows",
                "ram", "Оперативна пам'ять", "ram_of", "із", "dim", "Прозорість");
        same("de", "en", "site", "Server-Seite", "profile", "Profil", "min", "Minimieren", "close", "Schließen",
                "support", "Support", "updates", "Updates", "update", "Update",
                "folder", "Minecraft-\nOrdner", "mods", "Mods", "skin", "Skin", "version", "Version wählen",
                "settings", "Einstellungen", "more", "Mehr →", "cancel", "Abbrechen", "close_game", "Schließen",
                "launch", "Starten", "install", "Installieren", "update_btn", "Aktualisieren",
                "check", "Build prüfen", "checking", "Prüfung…", "starting", "Startet",
                "running", "Läuft", "update_mods", "Mods aktualisieren", "go", "Start",
                "srv_down", "Serverstatus nicht verfügbar", "srv_on", "Server online",
                "srv_off", "Server antwortet nicht", "players", "Spieler: ",
                "news", "Neuigkeiten", "news_down", "Neuigkeiten nicht verfügbar",
                "tab_main", "Allgemein", "tab_game", "Spiel", "tab_mods", "Mods", "tab_look", "Aussehen",
                "tab_extra", "Erweitert", "save", "Änderungen speichern", "reset", "Einstellungen zurücksetzen",
                "lang", "Sprache", "lang_pick", "Sprache wählen", "lang_sys", "Wie Windows",
                "ram", "Arbeitsspeicher", "ram_of", "von", "dim", "Transparenz");
        same("fr", "en", "settings", "Paramètres", "launch", "Jouer", "install", "Installer", "close", "Fermer",
                "support", "Assistance", "mods", "Mods", "skin", "Skin", "version", "Choisir la version",
                "profile", "Profil", "news", "Actualités", "save", "Enregistrer", "reset", "Réinitialiser",
                "lang", "Langue", "lang_sys", "Comme Windows", "ram", "Mémoire", "ram_of", "sur", "dim", "Transparence",
                "tab_main", "Général", "tab_game", "Jeu", "tab_mods", "Mods", "tab_look", "Apparence", "tab_extra", "Avancé",
                "update_btn", "Mettre à jour", "update_mods", "Mettre à jour les mods", "starting", "Démarrage",
                "running", "En cours", "srv_on", "Serveur en ligne", "players", "Joueurs : ");
        same("es", "en", "settings", "Ajustes", "launch", "Jugar", "install", "Instalar", "close", "Cerrar",
                "support", "Soporte", "mods", "Mods", "skin", "Skin", "version", "Elegir versión",
                "profile", "Perfil", "news", "Noticias", "save", "Guardar cambios", "reset", "Restablecer",
                "lang", "Idioma", "lang_sys", "Como Windows", "ram", "Memoria", "ram_of", "de", "dim", "Transparencia",
                "tab_main", "General", "tab_game", "Juego", "tab_mods", "Mods", "tab_look", "Apariencia", "tab_extra", "Avanzado",
                "update_btn", "Actualizar", "update_mods", "Actualizar mods", "starting", "Iniciando",
                "running", "En ejecución", "srv_on", "Servidor en línea", "players", "Jugadores: ");
        same("pl", "en", "settings", "Ustawienia", "launch", "Graj", "install", "Instaluj", "close", "Zamknij",
                "support", "Pomoc", "mods", "Mody", "skin", "Skin", "version", "Wybór wersji",
                "profile", "Profil", "news", "Aktualności", "save", "Zapisz zmiany", "reset", "Resetuj",
                "lang", "Język", "lang_sys", "Jak w Windows", "ram", "Pamięć", "ram_of", "z", "dim", "Przezroczystość",
                "tab_main", "Ogólne", "tab_game", "Gra", "tab_mods", "Mody", "tab_look", "Wygląd", "tab_extra", "Dodatkowe",
                "update_btn", "Aktualizuj", "update_mods", "Aktualizuj mody", "starting", "Uruchamianie",
                "running", "Uruchomiono", "srv_on", "Serwer online", "players", "Gracze: ");
        same("pt_br", "en", "settings", "Configurações", "launch", "Jogar", "install", "Instalar", "close", "Fechar",
                "lang", "Idioma", "lang_sys", "Igual ao Windows", "ram", "Memória", "ram_of", "de", "dim", "Transparência",
                "save", "Salvar", "reset", "Redefinir", "tab_main", "Geral", "tab_game", "Jogo", "tab_look", "Aparência");
        same("it", "en", "settings", "Impostazioni", "launch", "Gioca", "close", "Chiudi", "lang", "Lingua",
                "ram", "Memoria", "ram_of", "di", "dim", "Trasparenza", "save", "Salva", "reset", "Reimposta");
        same("tr", "en", "settings", "Ayarlar", "launch", "Oyna", "close", "Kapat", "lang", "Dil",
                "ram", "Bellek", "ram_of", "/", "dim", "Şeffaflık", "save", "Kaydet", "reset", "Sıfırla");
        same("zh_cn", "en", "settings", "设置", "launch", "开始", "install", "安装", "close", "关闭",
                "lang", "界面语言", "ram", "内存", "ram_of", "/", "dim", "透明度", "save", "保存", "reset", "重置",
                "mods", "模组", "skin", "皮肤", "news", "新闻", "support", "支持");
        same("ja", "en", "settings", "設定", "launch", "起動", "close", "閉じる", "lang", "言語",
                "ram", "メモリ", "ram_of", "/", "dim", "透明度", "save", "保存", "reset", "リセット");
        same("ko", "en", "settings", "설정", "launch", "실행", "close", "닫기", "lang", "언어",
                "ram", "메모리", "ram_of", "/", "dim", "투명도", "save", "저장", "reset", "초기화");
        same("nl", "en", "settings", "Instellingen", "launch", "Spelen", "close", "Sluiten",
                "lang", "Taal", "ram", "Geheugen", "ram_of", "van", "dim", "Transparantie",
                "save", "Opslaan", "reset", "Herstellen", "tab_main", "Algemeen", "tab_game", "Spel",
                "tab_look", "Uiterlijk", "tab_extra", "Extra");
        same("sv", "en", "settings", "Inställningar", "launch", "Spela", "close", "Stäng",
                "lang", "Språk", "ram", "Minne", "ram_of", "av", "dim", "Transparens",
                "save", "Spara", "reset", "Återställ");
        same("fi", "en", "settings", "Asetukset", "launch", "Pelaa", "close", "Sulje",
                "lang", "Kieli", "ram", "Muisti", "ram_of", "/", "dim", "Läpinäkyvyys",
                "save", "Tallenna", "reset", "Palauta");
        same("cs", "en", "settings", "Nastavení", "launch", "Hrát", "close", "Zavřít",
                "lang", "Jazyk", "ram", "Paměť", "ram_of", "z", "dim", "Průhlednost",
                "save", "Uložit", "reset", "Obnovit");
        same("sk", "en", "settings", "Nastavenia", "launch", "Hrať", "close", "Zavrieť",
                "lang", "Jazyk", "ram", "Pamäť", "ram_of", "z", "dim", "Priehľadnosť",
                "save", "Uložiť", "reset", "Obnoviť");
        same("hu", "en", "settings", "Beállítások", "launch", "Játék", "close", "Bezárás",
                "lang", "Nyelv", "ram", "Memória", "ram_of", "/", "dim", "Átlátszóság",
                "save", "Mentés", "reset", "Visszaállítás");
        same("ro", "en", "settings", "Setări", "launch", "Joacă", "close", "Închide",
                "lang", "Limbă", "ram", "Memorie", "ram_of", "din", "dim", "Transparență",
                "save", "Salvează", "reset", "Resetează");
        same("bg", "en", "settings", "Настройки", "launch", "Играй", "close", "Затвори",
                "lang", "Език", "ram", "Памет", "ram_of", "от", "dim", "Прозрачност",
                "save", "Запази", "reset", "Нулирай", "gb", "ГБ");
        same("da", "en", "settings", "Indstillinger", "launch", "Spil", "close", "Luk",
                "lang", "Sprog", "ram", "Hukommelse", "ram_of", "af", "dim", "Gennemsigtighed",
                "save", "Gem", "reset", "Nulstil");
        same("nb", "en", "settings", "Innstillinger", "launch", "Spill", "close", "Lukk",
                "lang", "Språk", "ram", "Minne", "ram_of", "av", "dim", "Gjennomsiktighet",
                "save", "Lagre", "reset", "Tilbakestill");
        same("el", "en", "settings", "Ρυθμίσεις", "launch", "Παιχνίδι", "close", "Κλείσιμο",
                "lang", "Γλώσσα", "ram", "Μνήμη", "ram_of", "από", "dim", "Διαφάνεια",
                "save", "Αποθήκευση", "reset", "Επαναφορά");
        same("id", "en", "settings", "Pengaturan", "launch", "Main", "close", "Tutup",
                "lang", "Bahasa", "ram", "Memori", "ram_of", "dari", "dim", "Transparansi",
                "save", "Simpan", "reset", "Atur ulang");
        same("ar", "en", "settings", "الإعدادات", "launch", "تشغيل", "close", "إغلاق",
                "lang", "اللغة", "ram", "الذاكرة", "ram_of", "من", "dim", "الشفافية",
                "save", "حفظ", "reset", "إعادة ضبط");
        same("be", "ru", "settings", "Налады", "launch", "Запусціць", "close", "Закрыць",
                "lang", "Мова", "ram", "Памяць", "ram_of", "з", "dim", "Празрыстасць",
                "save", "Захаваць змены", "reset", "Скінуць налады", "gb", "ГБ");
        same("kk", "ru", "settings", "Баптаулар", "launch", "Ойнау", "close", "Жабу",
                "lang", "Тіл", "ram", "Жады", "ram_of", "/", "dim", "Мөлдірлік",
                "save", "Сақтау", "reset", "Қалпына келтіру", "gb", "ГБ");
        copy("es_mx", "es");
        copy("pt_pt", "pt_br");
        copy("zh_tw", "zh_cn");
        put("ru", "foot_on", "Сервер: ", "logs_btn", "Открыть папку логов", "diag_btn", "Создать файл диагностики",
                "details", "Сведения", "add_bg", "Добавить фон", "reset_bg", "Сбросить фон",
                "folder_btn", "Папка", "gallery", "Галерея", "done", "Готово",
                "copy_addr", "Скопировать адрес", "copied", "Адрес скопирован");
        put("en", "foot_on", "Server: ", "logs_btn", "Open the logs folder", "diag_btn", "Create a diagnostics file",
                "details", "Details", "add_bg", "Add background", "reset_bg", "Reset background",
                "folder_btn", "Folder", "gallery", "Gallery", "done", "Done",
                "copy_addr", "Copy address", "copied", "Address copied");
        put("ru",
                "prof_title", "Профиль",
                "prof_current", "Текущий аккаунт",
                "prof_none", "Аккаунтов нет",
                "prof_none_h", "Создайте профиль, чтобы устанавливать версии и запускать игру.",
                "prof_ely", "Аккаунт Ely.by",
                "prof_off", "Оффлайн",
                "prof_rename", "Изменить имя",
                "prof_active", "Активный",
                "prof_info", "Информация об аккаунте",
                "prof_id", "ID аккаунта",
                "prof_platform", "Платформа",
                "prof_created", "Дата создания",
                "prof_status", "Статус",
                "prof_settings", "Настройки аккаунта",
                "prof_avatar", "Сменить аватарку",
                "prof_avatar_h", "Голова скина этого аккаунта. Если файла нет, остаётся значок профиля.",
                "prof_manage", "Управление аккаунтами",
                "prof_create", "Создать новый аккаунт",
                "prof_create_h", "Добавьте аккаунт Ely.by или оффлайн-ник.",
                "prof_delete", "Удалить аккаунт",
                "prof_delete_h", "Удалить выбранный аккаунт из лаунчера.",
                "prof_ely_in", "Вход через Ely.by",
                "prof_ely_h", "Войдите через Ely.by. Пароль вводится только на сайте, лаунчер его не видит.",
                "prof_ely_btn", "Войти через Ely.by",
                "prof_ely_cfg", "Параметры входа",
                "prof_list", "Список аккаунтов",
                "prof_empty_list", "Список пуст",
                "prof_foot", "Аватарка — голова скина этого аккаунта. Этот скин уходит в игру.",
                "prof_back", "Назад",
                "prof_need", "Сначала создайте профиль. Без аккаунта установка и запуск недоступны.",
                "prof_new", "Новый аккаунт",
                "prof_nick", "Ник",
                "prof_offline_btn", "Создать оффлайн",
                "prof_nick_bad", "Ник: от 3 до 16 символов, латиница, цифры и _.",
                "prof_nick_taken", "Такой оффлайн-ник уже есть.",
                "prof_ely_name", "Имя аккаунта Ely.by задаётся на сайте Ely.by.",
                "prof_del_ask", "Удалить аккаунт ",
                "ely_id", "Client id Ely.by",
                "ely_redirect", "Redirect URI",
                "ely_backend", "Сервер CW (https)");
        put("en",
                "prof_title", "Profile",
                "prof_current", "Current account",
                "prof_none", "No accounts",
                "prof_none_h", "Create a profile before installing a version or playing.",
                "prof_ely", "Ely.by account",
                "prof_off", "Offline",
                "prof_rename", "Change name",
                "prof_active", "Active",
                "prof_info", "Account information",
                "prof_id", "Account ID",
                "prof_platform", "Platform",
                "prof_created", "Created",
                "prof_status", "Status",
                "prof_settings", "Account settings",
                "prof_avatar", "Change avatar",
                "prof_avatar_h", "The head of this account's skin. Without a file, the profile icon stays.",
                "prof_manage", "Account management",
                "prof_create", "Create a new account",
                "prof_create_h", "Add an Ely.by account or an offline nickname.",
                "prof_delete", "Delete account",
                "prof_delete_h", "Remove the selected account from the launcher.",
                "prof_ely_in", "Sign in with Ely.by",
                "prof_ely_h", "Sign in on Ely.by. The password stays on their site.",
                "prof_ely_btn", "Sign in with Ely.by",
                "prof_ely_cfg", "Sign-in settings",
                "prof_list", "Account list",
                "prof_empty_list", "The list is empty",
                "prof_foot", "The avatar is the head of this account's skin. That skin is sent into the game.",
                "prof_back", "Back",
                "prof_need", "Create a profile first. Install and launch need an account.",
                "prof_new", "New account",
                "prof_nick", "Nickname",
                "prof_offline_btn", "Create offline",
                "prof_nick_bad", "Nickname: 3–16 letters, digits and _.",
                "prof_nick_taken", "That offline nickname already exists.",
                "prof_ely_name", "The Ely.by name is set on the Ely.by site.",
                "prof_del_ask", "Delete account ",
                "ely_id", "Ely.by client id",
                "ely_redirect", "Redirect URI",
                "ely_backend", "CW server (https)");
    }

    private Lang() {
    }

    public static void use(String minecraftCode) {
        String c = minecraftCode == null ? "" : minecraftCode.trim().toLowerCase(Locale.ROOT);
        if (c.isBlank()) c = "ru";
        if (c.startsWith("zh")) c = c.contains("tw") || c.contains("hk") ? "zh_tw" : "zh_cn";
        else if (c.startsWith("pt")) c = c.endsWith("_pt") ? "pt_pt" : "pt_br";
        else if (c.startsWith("es") && (c.contains("mx") || c.contains("_ar"))) c = "es_mx";
        else if (c.startsWith("no") || c.startsWith("nb") || c.startsWith("nn")) c = "nb";
        else if (!ALL.containsKey(c)) {
            int cut = c.indexOf('_');
            String two = cut > 0 ? c.substring(0, cut) : (c.length() >= 2 ? c.substring(0, 2) : c);
            c = ALL.containsKey(two) ? two : "en";
        }
        if (!ALL.containsKey(c)) c = "en";
        code = c;
    }

    public static boolean russian() {
        return code != null && code.startsWith("ru");
    }

    public static String t(String key) {
        String v = text(code, key);
        if (v != null) return v;
        v = text("en", key);
        if (v != null) return v;
        v = text("ru", key);
        return v == null ? key : v;
    }

    private static String text(String lang, String key) {
        Map<String, String> map = ALL.get(lang);
        return map == null ? null : map.get(key);
    }

    private static void put(String lang, String... pairs) {
        Map<String, String> map = ALL.computeIfAbsent(lang, k -> new HashMap<>());
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
    }

    private static void copy(String lang, String from) {
        ALL.put(lang, new HashMap<>(ALL.getOrDefault(from, Map.of())));
    }

    /** Копирует основу и перекрывает отдельные фразы. */
    private static void same(String lang, String base, String... pairs) {
        copy(lang, base);
        put(lang, pairs);
    }
}
