package dev.combatdata.ui;

import static dev.combatdata.core.Lang.t;

/** Methods rather than constants so the text follows language switches. */
final class Help {
    private Help() {
    }

    static String overview() {
        return t("Выводы сверху: самое важное, что видно по записи. Клик по выводу открывает нужный график. Ниже карточки с цифрами, «Подробнее» раскрывает остальное.",
                "Insights on top: the most important things in this recording. Click one to open the right chart. Below are cards with numbers, \"More\" shows the rest.", "Oben die Erkenntnisse: das Wichtigste aus dieser Aufnahme. Klick auf eine öffnet das passende Diagramm. Darunter Karten mit Zahlen, „Mehr“ zeigt den Rest.");
    }

    static String hits() {
        return t("Точность: сколько ваших атак сервер подтвердил уроном.\n«Не дотянулся»: прицел был на противнике, но он был дальше вашей дальности.\n«Без подтверждения»: удар ушёл, но урона не было (лаг, щит или неуязвимость после удара).",
                "Accuracy: how many of your attacks the server confirmed with damage.\n\"Out of range\": your crosshair was on them, but they were beyond your reach.\n\"Unconfirmed\": the hit was sent but no damage came (lag, shield or hurt immunity).", "Trefferquote: wie viele deiner Angriffe der Server mit Schaden bestätigt hat.\n„Zu weit weg“: Dein Fadenkreuz war auf ihm, aber er war außerhalb deiner Reichweite.\n„Unbestätigt“: Der Schlag wurde gesendet, aber es kam kein Schaden (Lag, Schild oder Unverwundbarkeit nach einem Treffer).");
    }

    static String jump() {
        return t("Jump reset: прыжок сразу после полученного удара гасит часть отбрасывания.\nУспех: прыжок попал в окно после удара (окно в Настройки → Дополнительно). Рано или поздно: прыжок был, но мимо окна.\nFly out: прыжок с зажатой S, чтобы улететь дальше. В jump reset не считается.",
                "Jump reset: jumping right after you get hit cancels part of the knockback.\nSuccess: the jump landed in the window after the hit (window in Settings → Advanced). Early or late: you jumped but missed the window.\nFly out: a jump while holding S to fly further. Not counted as a jump reset.", "Jump Reset: Springst du direkt nach einem Treffer, hebst du einen Teil des Rückstoßes auf.\nErfolg: Der Sprung lag im Fenster nach dem Treffer (Fenster unter Einstellungen → Erweitert). Zu früh oder zu spät: Du bist gesprungen, aber außerhalb des Fensters.\nFly out: ein Sprung mit gehaltenem S, um weiter wegzufliegen. Zählt nicht als Jump Reset.");
    }

    static String reach() {
        return t("Расстояние от ваших глаз до места, куда попал удар. «Дальше дальности»: удар засчитан, хотя на вашем экране противник был дальше вашей дальности атаки (так бывает из-за пинга).",
                "Distance from your eyes to the spot the hit landed. \"Past range\": the hit counted although on your screen they were beyond your attack range (ping does that).", "Abstand von deinen Augen bis zur Trefferstelle. „Über Reichweite“: Der Treffer zählte, obwohl der Gegner auf deinem Bildschirm außerhalb deiner Reichweite war (das macht der Ping).");
    }

    static String aim() {
        return t("Прицел на противнике: доля взмахов, при которых прицел был на нём (промах «не дотянулся» считается: прицел был верный). Промахи разложены по сторонам: куда ушёл прицел мимо тела. Удары вплотную, ближе 1 блока, не считаются.",
                "Crosshair on opponent: share of swings with the crosshair on them (an out-of-range miss counts: the aim was right). Misses are split by side: where the crosshair passed the body. Point-blank swings closer than 1 block are skipped.", "Fadenkreuz auf Gegner: Anteil der Schläge, bei denen das Fadenkreuz auf ihm war (ein „zu weit weg“ zählt mit: das Ziel war richtig). Fehlschläge sind nach Seite aufgeteilt: wo das Fadenkreuz am Körper vorbeiging. Schläge aus nächster Nähe, unter 1 Block, werden nicht gezählt.");
    }

    static String threeBlock() {
        return t("3 block hit: ваш удар засчитался, хотя на экране противника вы были дальше 3 блоков. Из-за пинга он видит вас с опозданием, и если вы шли к нему (или он от вас), ответить в этот момент он не мог. Это оценка по скоростям и пингу.",
                "3 block hit: your hit counted although on the opponent's screen you were beyond 3 blocks. Ping makes them see you late, and if you were moving in (or they were moving away) they could not hit back at that moment. Estimated from speeds and ping.", "3 Block Hit: Dein Treffer zählte, obwohl du auf dem Bildschirm des Gegners weiter als 3 Blöcke weg warst. Durch den Ping sieht er dich verspätet, und wenn du auf ihn zugelaufen bist (oder er weg), konnte er in dem Moment nicht zurückschlagen. Geschätzt aus Geschwindigkeiten und Ping.");
    }

    static String combo() {
        return t("Комбо: сколько ваших ударов прошло подряд, пока противник не ударил в ответ. Размен (нанесено к получено) больше 1 значит, что вы били чаще.",
                "Combo: how many of your hits landed in a row before the opponent hit back. A trade ratio (dealt to taken) above 1 means you hit more often.", "Combo: wie viele deiner Treffer hintereinander saßen, bevor der Gegner zurückschlug. Ein Verhältnis (ausgeteilt zu eingesteckt) über 1 heißt, dass du öfter getroffen hast.");
    }

    static String trades() {
        return t("Размен: короткий обмен ударами с одним противником. Новый начинается, если ударов не было дольше 1,5 с. Побеждает тот, у кого больше ударов.\nВ скобках счёт по весу: крит 1,5, ваш слабый удар меньше 1.\nПричина проигрыша: что было перед его первым ударом (ваш промах, удар без заряда, 3 block hit против вас или неудачный jump reset).",
                "Trade: a short exchange of hits with one opponent. A new one starts after more than 1.5 s without hits. Whoever lands more hits wins.\nIn brackets is the score by weight: a crit is 1.5, your weak hit is less than 1.\nLoss cause: what happened before their first hit (your miss, an uncharged hit, a 3 block hit against you or a failed jump reset).", "Trade: ein kurzer Schlagabtausch mit einem Gegner. Ein neuer beginnt nach mehr als 1,5 s ohne Treffer. Wer mehr Treffer landet, gewinnt.\nIn Klammern der Punktestand nach Gewicht: ein Crit zählt 1,5, dein schwacher Schlag weniger als 1.\nUrsache der Niederlage: was vor seinem ersten Treffer passiert ist (dein Fehlschlag, ein ungeladener Schlag, ein 3 Block Hit gegen dich oder ein misslungener Jump Reset).");
    }

    static String charge() {
        return t("Заряд: насколько восстановилась атака после прошлого взмаха. Выше 90% удар полный: больше урона, и только такой удар бывает с разбега или критом. Слишком частые клики бьют без заряда.",
                "Charge: how much your attack recovered since the last swing. Above 90% the hit is full: more damage, and only a full hit can be a sprint hit or a crit. Clicking too fast hits without charge.", "Aufladung: wie weit sich dein Angriff seit dem letzten Schlag erholt hat. Über 90 % ist der Schlag voll: mehr Schaden, und nur ein voller Schlag kann ein Sprint-Treffer oder Crit sein. Zu schnelles Klicken schlägt ohne Aufladung.");
    }

    static String types() {
        return t("С разбега: полный удар на бегу, сильнее отбрасывает.\nКрит: удар в падении после прыжка, урон × 1,5.\nСлабый: без полного заряда.\nРазящий: полный удар мечом стоя, задевает тех, кто рядом.",
                "Sprint: a full hit while sprinting, more knockback.\nCrit: a hit while falling after a jump, damage × 1.5.\nWeak: not fully charged.\nSweep: a full sword hit while standing, also hits those nearby.", "Sprint: ein voller Schlag im Sprint, mehr Rückstoß.\nCrit: ein Schlag im Fallen nach einem Sprung, Schaden × 1,5.\nSchwach: nicht voll aufgeladen.\nSweep: ein voller Schwertschlag im Stehen, trifft auch Spieler in der Nähe.");
    }

    static String ping() {
        return t("Ваш пинг до сервера во время записи. Скачки пинга искажают jump reset и 3 block hit.",
                "Your ping to the server during the recording. Ping spikes distort jump reset and 3 block hit.", "Dein Ping zum Server während der Aufnahme. Ping-Spitzen verfälschen Jump Reset und 3 Block Hit.");
    }

    static String taken() {
        return t("С какого расстояния противник бил вас, как это было видно на вашем экране. 3 block hit против вас: он ударил, когда был дальше вашей дальности, и ответить вы не могли.",
                "How far the opponent was when they hit you, as seen on your screen. 3 block hit against you: they hit while beyond your reach, so you could not hit back.", "Wie weit der Gegner weg war, als er dich traf, so wie es auf deinem Bildschirm aussah. 3 Block Hit gegen dich: Er traf von außerhalb deiner Reichweite, du konntest also nicht zurückschlagen.");
    }

    static String wtap() {
        return t("После удара с разбега спринт сбрасывается. Если клавиша спринта зажата, он вернётся сам через тик. Если нет, спринт надо вернуть: отпустить и снова нажать W (W-tap) или коротко нажать S (S-tap). Без спринта следующий удар отбрасывает слабее.",
                "A sprint hit resets your sprint. If you hold the sprint key, it comes back a tick later by itself. If not, you have to bring it back: release and press W again (W-tap) or tap S (S-tap). Without sprint the next hit knocks back less.", "Ein Sprint-Treffer setzt deinen Sprint zurück. Hältst du die Sprinttaste, kommt er einen Tick später von selbst wieder. Wenn nicht, musst du ihn zurückholen: W loslassen und neu drücken (W-Tap) oder kurz S drücken (S-Tap). Ohne Sprint hat der nächste Treffer weniger Rückstoß.");
    }

    static String hitsChart() {
        return t("Каждая точка: ваша атака, по высоте дистанция удара. Зелёные попали, жёлтые 3 block hit, красные промах, серые без подтверждения. Пунктир: ваша дальность.\nКолесо: масштаб, перетаскивание: сдвиг, клик по точке: подробности.",
                "Each dot is your attack, height is the hit distance. Green hit, yellow 3 block hit, red miss, grey unconfirmed. Dashed line: your range.\nWheel: zoom, drag: pan, click a dot: details.", "Jeder Punkt ist dein Angriff, die Höhe ist die Trefferdistanz. Grün Treffer, gelb 3 Block Hit, rot Fehlschlag, grau unbestätigt. Gestrichelte Linie: deine Reichweite.\nMausrad: Zoom, Ziehen: verschieben, Klick auf Punkt: Details.");
    }

    static String aimTab() {
        return t("Силуэт: каждый взмах там, где был прицел. Зелёные попали, жёлтые 3 block hit, серые «не дотянулся», красные промахи (за краем — дальше от тела). Зелёная полоса и крест: куда лучше целиться (грудь–шея: больше всего запаса, если противник двигается или прыгает). Второй крест: ваш средний прицел, линия показывает, куда его сдвинуть.\n«Что улучшить»: советы по этой записи, самое важное сверху.\n«По ситуациям»: как часто прицел был на противнике, когда он стрейфит или прыгает, когда вы резко крутите мышь, в прыжке и на разной дистанции. «Позади» против стрейфа значит, что прицел не успевает за противником.",
                "Silhouette: each swing where your crosshair was. Green hit, yellow 3 block hit, grey out of range, red misses (at the edge means further off). Green band and cross: the best place to aim (chest to neck: the most room when they move or jump). The other cross is your average aim, the line shows which way to move it.\n\"What to improve\": tips for this recording, most important first.\n\"Aim by situation\": how often the crosshair was on them while they strafe or jump, while you flick the mouse, in the air and at each distance. \"Behind\" against strafes means your crosshair cannot keep up with them.", "Silhouette: jeder Schlag dort, wo dein Fadenkreuz war. Grün Treffer, gelb 3 Block Hit, grau zu weit weg, rot Fehlschläge (am Rand heißt weiter daneben). Grünes Band und Kreuz: die beste Stelle zum Zielen (Brust bis Hals: dort bleibt am meisten Spielraum, wenn er läuft oder springt). Das andere Kreuz ist dein durchschnittliches Ziel, die Linie zeigt, in welche Richtung du es verschieben solltest.\n„Was verbessern“: Tipps für diese Aufnahme, das Wichtigste zuerst.\n„Zielen nach Situation“: wie oft das Fadenkreuz auf ihm war, während er strafet oder springt, während du die Maus schnell drehst, in der Luft und je nach Distanz. „Dahinter“ gegen Strafes heißt, dein Fadenkreuz kommt nicht hinterher.");
    }

    static String jumpChart() {
        return t("Каждая точка: удар по вам. По высоте: через сколько мс после него вы прыгнули (0 — момент удара, минус — до удара). Зелёная полоса: окно успеха. Метки внизу: удары без прыжка.",
                "Each dot is a hit on you. Height: how many ms after it you jumped (0 is the hit, minus is before it). Green band: the success window. Ticks at the bottom: hits without a jump.", "Jeder Punkt ist ein Treffer auf dich. Höhe: wie viele ms danach du gesprungen bist (0 ist der Treffer, minus ist davor). Grünes Band: das Erfolgsfenster. Striche unten: Treffer ohne Sprung.");
    }

    static String knockback() {
        return t("Насколько вас отбросило за полсекунды после удара в зависимости от момента прыжка. Самые низкие столбцы: лучшее время для прыжка на этом сервере и пинге. По ним можно подобрать окно jump reset.",
                "How far you were knocked back in half a second depending on when you jumped. The lowest bars are the best time to jump on this server and ping. The jump reset window can be fitted from them.", "Wie weit du in einer halben Sekunde zurückgestoßen wurdest, je nachdem, wann du gesprungen bist. Die niedrigsten Balken sind der beste Sprungzeitpunkt auf diesem Server mit deinem Ping. Daraus lässt sich das Jump-Reset-Fenster anpassen.");
    }

    static String opponents() {
        return t("Клик по строке: вся статистика только по этому противнику. Ещё клик или × на чипе снимает фильтр.",
                "Click a row: all stats for this opponent only. Click again or × on the chip to clear.", "Klick auf eine Zeile: alle Werte nur für diesen Gegner. Nochmal klicken oder × am Chip zum Aufheben.");
    }

    static String fights() {
        return t("Бой: удары без паузы больше 10 с. Клик по бою открывает его размены. Совсем короткие стычки скрываются (Настройки → Фильтры статистики).",
                "A fight is hits without a pause over 10 s. Click a fight to see its trades. Very short skirmishes are hidden (Settings → Stat filters).", "Ein Kampf sind Treffer ohne Pause über 10 s. Klick auf einen Kampf zeigt seine Trades. Sehr kurze Gefechte werden ausgeblendet (Einstellungen → Statistikfilter).");
    }

    static String distance() {
        return t("Линия: расстояние до противника во время боя (от ваших глаз до его тела). Отметки: удары, ваши зелёные, его красные, жёлтые — 3 block hit. Пунктир: ваша дальность.\n«Повтор» показывает бой сверху; клик по графику переносит повтор в этот момент.",
                "Line: distance to the opponent during the fight (from your eyes to their body). Marks are hits: yours green, theirs red, yellow are 3 block hits. Dashed line: your range.\n\"Replay\" shows the fight from above; click the chart to jump the replay there.", "Linie: Distanz zum Gegner während des Kampfs (von deinen Augen zu seinem Körper). Markierungen sind Treffer: deine grün, seine rot, gelb sind 3 Block Hits. Gestrichelte Linie: deine Reichweite.\n„Wiederholung“ zeigt den Kampf von oben; Klick ins Diagramm springt in der Wiederholung dorthin.");
    }

    static String timeline() {
        return t("Бои, метки и смены сервера по порядку.", "Fights, markers and server changes in order.", "Kämpfe, Markierungen und Serverwechsel der Reihe nach.");
    }

    static String hidden() {
        return t("Эти удары не входят в статистику, но остаются в записи. Что отсекать, задаётся в Настройки → Фильтры статистики. «Показать» включает их только для этой записи.",
                "These hits are left out of the stats but stay in the recording. What gets filtered is set in Settings → Stat filters. \"Show\" brings them back for this recording only.", "Diese Treffer zählen nicht für die Statistik, bleiben aber in der Aufnahme. Was gefiltert wird, stellst du unter Einstellungen → Statistikfilter ein. „Anzeigen“ holt sie nur für diese Aufnahme zurück.");
    }

    static String progress() {
        return t("Каждая точка: запись или день. Точки идут по порядку через равные промежутки, даты снизу. Точка появляется, только если данных хватает (например, от 5 атак). Пунктир: среднее. Клик по точке открывает запись.",
                "Each dot is a recording or a day, evenly spaced in order, dates below. A dot appears only with enough data (e.g. 5+ attacks). Dashed line: the average. Click a dot to open the recording.", "Jeder Punkt ist eine Aufnahme oder ein Tag, der Reihe nach gleichmäßig verteilt, Datum unten. Ein Punkt erscheint nur mit genug Daten (z. B. ab 5 Angriffen). Gestrichelte Linie: der Durchschnitt. Klick auf einen Punkt öffnet die Aufnahme.");
    }

    static String interp() {
        return t("Игра противника показывает вас чуть в прошлом, чтобы движение было плавным. Обычно около 100 мс. Нужно только для оценки 3 block hit.",
                "The opponent's game shows you slightly in the past to keep movement smooth. Usually about 100 ms. Only used to estimate 3 block hits.", "Das Spiel des Gegners zeigt dich leicht in der Vergangenheit, damit die Bewegung flüssig ist. Meist etwa 100 ms. Wird nur für die Schätzung von 3 Block Hits gebraucht.");
    }

    static String calibrate() {
        return t("Ищет момент прыжка, при котором вас меньше всего отбрасывало, по всем сохранённым записям. Ширина окна остаётся вашей, сдвигается только его положение.",
                "Finds the jump timing that gave you the least knockback across all saved recordings. Your window width stays, only its position moves.", "Sucht den Sprungzeitpunkt, bei dem du über alle gespeicherten Aufnahmen am wenigsten Rückstoß hattest. Die Fensterbreite bleibt, nur die Lage verschiebt sich.");
    }

    static String replay() {
        return t("Бой сверху: вы зелёный квадрат, противник красный, остальные серые (размер как хитбокс). Чёрточка показывает, куда смотрит игрок, хвост — путь за секунду. Сетка — блоки, каждая пятая линия ярче.\nВ прыжке игрок поднимается над тенью и обводится светлой рамкой. После удара по вам над вами ярлык: «JR ✔» — jump reset в окне, «рано»/«поздно» — мимо, «fly out», «без прыжка».\nВспышка — удар (жёлтая — 3 block hit), круг — ваша дальность. Колесо над видом — масштаб, ползунок перематывает, ‹ и › — шаг на тик.",
                "The fight from above: you are the green square, the opponent red, others grey (hitbox size). The tick shows where a player looks, the tail is the last second. The grid is blocks, every fifth line brighter.\nIn a jump a player rises above their shadow with a light outline. After a hit on you a tag shows above you: \"JR ✔\" is a jump reset in the window, \"early\"/\"late\" missed it, \"fly out\", \"no jump\".\nA flash is a hit (yellow is a 3 block hit), the ring is your range. Wheel over the view zooms, the slider seeks, ‹ and › step one tick.", "Der Kampf von oben: Du bist das grüne Quadrat, der Gegner rot, andere grau (Hitbox-Größe). Der Strich zeigt die Blickrichtung, der Schweif die letzte Sekunde. Das Raster sind Blöcke, jede fünfte Linie heller.\nIm Sprung hebt sich ein Spieler über seinen Schatten und bekommt einen hellen Rand. Nach einem Treffer auf dich erscheint über dir: „JR ✔“ ist ein Jump Reset im Fenster, „zu früh“/„zu spät“ daneben, „Fly out“, „kein Sprung“.\nEin Blitz ist ein Treffer (gelb ist ein 3 Block Hit), der Ring ist deine Reichweite. Mausrad über der Ansicht zoomt, der Regler spult, ‹ und › gehen einen Tick weiter.");
    }

    static String recordMode() {
        return t("Полная: пишется всё. Лёгкая: без позиций, нажатий клавиш и отлёта. Своя: вы сами выбрали, что писать.\nЗаметно место экономят только позиции (около 0,7 МБ на 20 минут боя), остальное мелочь: его выключают, чтобы не было лишних экранов.\nВыключенное не попадает в новые записи, и в Просмотре такие экраны пишут «не записывалось». Старые записи не меняются.",
                "Full: everything is recorded. Lite: no positions, key presses or knockback. Custom: you picked what to record.\nOnly positions take noticeable space (about 0.7 MB per 20 minutes of fighting), the rest is tiny: turn it off to hide screens you do not use.\nWhat is off is not saved in new recordings, and those screens in View say \"not recorded\". Old recordings do not change.", "Voll: alles wird aufgenommen. Leicht: ohne Positionen, Tastendrücke und Rückstoß. Eigene: du wählst, was aufgenommen wird.\nNur Positionen brauchen merklich Platz (etwa 0,7 MB pro 20 Minuten Kampf), der Rest ist winzig: schalte ab, was du nicht nutzt, damit die Bildschirme dazu verschwinden.\nWas aus ist, wird in neuen Aufnahmen nicht gespeichert, und diese Bildschirme zeigen „nicht aufgenommen“. Alte Aufnahmen ändern sich nicht.");
    }

    static String fightSummary() {
        return t("После боя (по умолчанию через 10 секунд без ударов, на 5 секунд) рядом с надписью REC появляется итог: попадания, jump reset, лучшее комбо, размены (выиграно:проиграно); по желанию дистанция, 3 block hit и полученные удары. Во время боя ничего не показывается. Учитываются фильтры статистики, бои меньше минимального размера не показываются.\nНиже настраивается, что показывать, вид, цвета, фон, масштаб, время и место. Пример сверху показывает, как будет выглядеть после «Применить».",
                "After a fight (by default after 10 seconds without hits, for 5 seconds) a summary shows next to the REC label: hits, jump reset, best combo, trades (won:lost); optionally reach, 3 block hits and hits taken. Nothing shows during a fight. Stat filters apply, fights below the minimum size are skipped.\nBelow you choose what to show, layout, colors, background, scale, timing and place. The example on top shows how it will look after Apply.", "Nach einem Kampf (standardmäßig nach 10 Sekunden ohne Treffer, für 5 Sekunden) erscheint neben REC eine Zusammenfassung: Treffer, Jump Reset, beste Combo, Trades (gewonnen:verloren); optional Reichweite, 3 Block Hits und eingesteckte Treffer. Während eines Kampfs wird nichts angezeigt. Statistikfilter gelten, Kämpfe unter der Mindestgröße werden übersprungen.\nUnten wählst du, was angezeigt wird, Layout, Farben, Hintergrund, Größe, Zeit und Position. Das Beispiel oben zeigt, wie es nach „Übernehmen“ aussieht.");
    }

    static String themes() {
        return t("Тема меняет цвета меню. Выбор видно сразу, сохраняется по «Применить».\nВстроенные темы не меняются: «Создать копию» делает свою, у неё можно поменять каждый цвет (клик по цвету: ползунки, клик по коду: ввести hex) и название.\n«Копировать код» кладёт тему в буфер обмена строкой, её можно отправить другу; «Вставить код» добавляет тему из буфера обмена.",
                "A theme changes the menu colors. You see the choice right away, it is saved on Apply.\nBuilt-in themes do not change: \"Make a copy\" creates your own, where every color (click a color: sliders, click its code: type hex) and the name can be changed.\n\"Copy code\" puts the theme on the clipboard as text you can send to a friend; \"Paste code\" adds a theme from the clipboard.", "Ein Theme ändert die Menüfarben. Du siehst die Auswahl sofort, gespeichert wird mit „Übernehmen“.\nEingebaute Themes ändern sich nicht: „Kopie erstellen“ legt dein eigenes an, bei dem du jede Farbe (Klick auf Farbe: Regler, Klick auf Code: Hex eingeben) und den Namen ändern kannst.\n„Code kopieren“ legt das Theme als Text in die Zwischenablage, den du einem Freund schicken kannst; „Code einfügen“ fügt ein Theme aus der Zwischenablage hinzu.");
    }
}
