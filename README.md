# MiniHome TV (`com.minihome.tv`)

> **EN:** A tiny, stable Android TV player for Turkish free-to-air live channels. Kotlin + Media3 ExoPlayer (HLS), one reusable player, bounded recovery, no ads/analytics. Debug APK ~3.9 MB, release ~0.64 MB. Tested on a low-end Android 9 projector (820 MB RAM).
>
> **Yayınlar hakkında:** Uygulama hiçbir içerik barındırmaz. `channels.json` içindeki adresler, yayıncıların herkese açık ve ücretsiz yayın adresleridir; hakları ilgili yayıncılara aittir. Ücretli, DRM'li ya da izinsiz yeniden yayın adresi eklenmez.

Android TV için küçük, tek amaçlı bir Türk ulusal yayın oynatıcısı. Kotlin, Media3 ExoPlayer ve HLS kullanır.
Kod sıfırdan yazıldı. `com.mt.turkeytv` uygulamasından alınan tek şey davranış tarifi: `name,url` listesi ve ExoPlayer ile oynatma.

```
./gradlew clean assembleDebug        # app/build/outputs/apk/debug/app-debug.apk  (~3.9 MB)
./gradlew assembleRelease            # R8 ile küçültülmüş, imzasız (~0.64 MB)
./gradlew testDebugUnitTest          # kanal listesi ayrıştırma testi
tools/smoke.sh [all|cold|home|rapid|sleep|prefs|net|zap|long]   # cihaz/emülatör testleri (adb)
```

Gereksinimler: JDK 17+ (21 ile denendi) ve Android SDK 36. Wrapper Gradle 9.8.0'ı indirir, AGP 9.4.1 kullanılıyor.

## Mimari

```
MainActivity  ──►  ChannelRepository   (assets/channels.json + SharedPreferences)
     │
     └────────►  PlayerController  ──►  tek ExoPlayer (HlsMediaSource, SurfaceView)
```

| Dosya | Görevi |
|---|---|
| `MainActivity.kt` | Yaşam döngüsü, kumanda tuşları, OSD, kanal listesi, ağ geri dönüşü |
| `PlayerController.kt` | Tek ExoPlayer, kurtarma merdiveni, takılma bekçisi (watchdog), en-boy uyumu |
| `ChannelRepository.kt` | `Channel` modeli, JSON ayrıştırma, son kanal ve favoriler |
| `assets/channels.json` | 23 kanal (ulusal + spor) (`id, number, name, url, backup[]`) |

Bağımlılıklar yalnızca `media3-exoplayer` ve `media3-exoplayer-hls`. AppCompat, media3-ui, reklam, analitik, WorkManager, Room ve servis/receiver/provider yok.

## Yaşam döngüsü

- Oynatıcı `onStart` içinde kurulur, `onStop` içinde **release** edilir. Home tuşu, TV uykusu ve üstte açılan başka bir uygulama bu yoldan geçer, yani arka planda hiçbir decoder ya da ağ bağlantısı kalmaz.
- `onStop` ayrıca Handler'daki tüm görevleri siler ve ağ callback'inin kaydını kaldırır. `PlayerController` referansı null yapılır.
- Herhangi bir anda en fazla bir ExoPlayer vardır. Kurtarma için yenisi gerektiğinde önce eskisi release edilir.
- `singleTask` ve `configChanges` sayesinde Activity yeniden yaratılmaz. Geri tuşu önce listeyi kapatır, sonra `finish()` çağırır. API 33+ için `OnBackInvokedCallback`, daha eskiler için `onBackPressed` kullanılır.

## Oynatma ve kurtarma

Kanal değişimi aynı oynatıcı üzerinde `setMediaItem → prepare → play` sırasıyla yapılır.

Her kanal için kurtarma sırası sınırlıdır:
`birincil → birincil tekrar → yeni ExoPlayer ile birincil → yedek 1 → yedek 2 → "kullanılamıyor"`

- Tetikleyiciler: `onPlayerError`, canlı yayının bitmesi (`STATE_ENDED`) ve **10 sn boyunca ilerleme olmaması**. Bekçi 2 sn'de bir `currentPosition` değerine bakar.
- Decoder hatası (4xxx) alınırsa bir sonraki deneme her durumda yeni bir oynatıcıyla yapılır.
- `BEHIND_LIVE_WINDOW` hatasında canlı uca atlanır, bu bir deneme sayılmaz.
- Merdiven yalnızca 30 sn kesintisiz oynatmadan sonra sıfırlanır, böylece sonsuz döngü oluşmaz.
- İnternet kesilirse "İnternet bağlantısı yok" gösterilir. Bağlantı geri gelince kanal kendiliğinden yeniden başlar (`NetworkCallback`, yalnızca ön plandayken).
- Tamponlar küçük tutuldu (10–30 sn, başlangıç için 1 sn), hem düşük RAM hem hızlı kanal geçişi için.

## Kumanda

| Tuş | Ne yapar |
|---|---|
| ▲ / ▼ | kanal listesini açar, imleç bir üst/alt kanaldadır. Listede ▲/▼ gezinir, OK seçili kanala geçer. 6 sn tuşa basılmazsa liste kapanır. Liste odak almaz; imleci uygulama kendisi yönetir, bu sayede air-mouse kumandalarda da çalışır. |
| CH+ / CH− | doğrudan sonraki/önceki kanala geçer |
| OK / MENU | kanal listesi. İzlenen kanal ▶ ile işaretli. Uzun OK basışı favori ekler/çıkarır (★) |
| ◄ ► / INFO | OSD'yi gösterir |
| 0–9 | numara girilir, 1,5 sn sonra o kanala geçilir |
| BACK | açıksa listeyi kapatır, değilse uygulamadan çıkar |

## Kalıcı durum

SharedPreferences içinde yalnızca `last_channel` (id) ve `favorites` tutulur. Her okuma `try/catch` ile korunur. Değer yanlış tipteyse ya da dosya bozuksa tercihler silinir, varsayılanlarla devam edilir. Uygulamanın açılması hiçbir durumda kaydedilmiş duruma bağlı değildir.

## Kanal listesi

`app/src/main/assets/channels.json`. Adresler 2026-10-03'te `curl` ile ve emülatörde tek tek oynatılarak kontrol edildi, tüm kanallar açıldı.
Liste yalnızca yayıncıların kendi CDN'lerindeki ücretsiz yayınlardan oluşuyor. Üçüncü taraf yeniden yayınlar, DRM'li ya da ücretli yayınlar yok.
Bazı adreslerde yayıncının kendi `app=`/`sid=` parametreleri var (Star, TV8, Kanal D). Bunlar ileride değişebilir, o zaman yalnızca JSON güncellenir.
Bir kanala yedek adres eklemek için `"backup": ["https://..."]` alanı kullanılır.

## Performans ve kararlılık kararları

1. **Tek ExoPlayer, `onStart`'ta kurulur, `onStop`'ta release edilir.** Ölçülen cihazda aynı anda en fazla 2 AVC decoder çalışabiliyor. Bu kuralla uygulama hiçbir zaman ikinci bir decoder açmıyor.
2. **Kanal değişiminde oynatıcı yeniden yaratılmaz.** Kanal `setMediaItem` ile değiştirilir. Oynatıcı yalnızca kurtarma sırasında ya da decoder hatasında yeniden yaratılır.
3. **Kurtarma sınırlıdır:** en fazla 3 deneme + yedek adresler, sonra "kullanılamıyor". Sıra yalnızca 30 sn kararlı oynatmadan sonra sıfırlanır.
4. **Bekçi süreleri:** oynatma sırasında 10 sn ilerleme yoksa, ilk görüntü 12 sn içinde gelmezse kurtarma başlar.
5. **Küçük tamponlar** (10–30 sn, başlangıç için 1 sn): RAM düşük kalır ve kanal geçişi hızlı olur.
6. **`DefaultMediaSourceFactory` yerine doğrudan `HlsMediaSource.Factory`:** DASH, SmoothStreaming ve RTSP kodu R8 ile tamamen atılıyor.
7. **`media3-ui` ve AppCompat kullanılmıyor.** Görüntü düz bir `SurfaceView` üzerinde, en-boy uyumu ise yaklaşık 10 satırlık kodla yapılıyor. Bu sayede release APK 0,64 MB.
8. **Ağ geri gelince otomatik deneme**, yalnızca uygulama ön plandayken çalışan bir `NetworkCallback` ile yapılır. Receiver ya da servis yok.
9. **Kalıcı duruma hiçbir zaman güvenilmez.** Her okuma `try/catch` ile korunur, sorun olursa tercihler silinir.
10. **`configChanges` + `singleTask`:** Activity yeniden yaratılmaz ve ikinci bir kopyası açılmaz.

## Lisans

MIT, bkz. [LICENSE](LICENSE).
