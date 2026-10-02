# MiniHome TV

**Android TV için küçük, kararlı ve reklamsız Türk ulusal yayın oynatıcısı.**
Aç, kanal başlasın. Kumandayla değiştir. Başka bir şey yapmasın.

![Lisans: MIT](https://img.shields.io/badge/lisans-MIT-green)
![Android 6.0+](https://img.shields.io/badge/Android-6.0%2B-3DDC84)
![APK 0.64 MB](https://img.shields.io/badge/release%20APK-0.64%20MB-blue)
![Reklam yok](https://img.shields.io/badge/reklam-yok-lightgrey)
![İzleme yok](https://img.shields.io/badge/izleme-yok-lightgrey)

> **English:** MiniHome TV is a tiny, open-source Android TV player for Turkish free-to-air live channels.
> Kotlin + Media3 ExoPlayer (HLS), a single reusable player, a bounded recovery ladder, no ads, no analytics, no background services.
> Release APK is 0.64 MB. It was built for and tested on a low-end Android 9 projector with 820 MB RAM. MIT licensed.

---

## İçindekiler

- [Neden MiniHome TV?](#neden-minihome-tv)
- [Ölçülen sonuçlar](#ölçülen-sonuçlar)
- [Özellikler](#özellikler)
- [Kurulum](#kurulum)
- [Kumanda](#kumanda)
- [Kanal listesi](#kanal-listesi)
- [Kendi projende kullanmak](#kendi-projende-kullanmak)
- [Mimari](#mimari)
- [Kararlılık nasıl sağlanıyor](#kararlılık-nasıl-sağlanıyor)
- [Test](#test)
- [Katkı](#katkı)
- [SSS](#sss)
- [Yasal not](#yasal-not)
- [Lisans](#lisans)

---

## Neden MiniHome TV?

Ücretsiz canlı TV uygulamalarının çoğu ucuz TV kutularında ve projeksiyonlarda zamanla bozuluyor: siyah ekran, açılmayan kanal, birkaç kanal sonra donma, önbellek temizlenmeden açılmama.

Bu proje böyle bir uygulamanın yaşattığı sorunlardan doğdu. O uygulamayı incelediğimizde sebep çok basitti:

- Ekrana her dönüşte **yeni bir oynatıcı yaratılıyor**, eskisi kapatılmıyordu.
- TV'lerde aynı anda çalışabilen video decoder sayısı genelde 2–4. Bir reklamdan, kanal değişiminden ya da uykudan dönünce sızan oynatıcılar decoder'ları tüketiyor, yeni kanal açılamıyordu.
- Hata yakalama ve yeniden deneme yoktu. Yayın bir kez koptuğunda ekran siyah kalıyordu.
- Uygulamanın asıl işiyle ilgisi olmayan reklam, onay formu ve arka plan iş kütüphaneleri RAM ve açılış süresi yiyordu.

MiniHome TV aynı temel davranışı (bir `ad, adres` listesi ve ExoPlayer ile HLS oynatma) **sıfırdan, başka bir uygulamanın kodunu kullanmadan** yeniden yazar ve bu sorunları tasarım gereği ortadan kaldırır:

| | Tipik ücretsiz canlı TV uygulaması | MiniHome TV |
|---|---|---|
| Oynatıcı sayısı | Her ekrana dönüşte ya da kanal değişiminde yenisi | **Her zaman tek oynatıcı.** Kanal değişince aynı oynatıcı kullanılır. |
| Decoder sızıntısı | Zamanla birikir, "açılmıyor / siyah ekran" | Uygulama arka plana geçince oynatıcı kapatılır, decoder anında serbest kalır |
| Yayın koparsa | Siyah ekran, uygulamayı kapatıp açmak gerekir | Sınırlı yeniden deneme, yedek adres, internet gelince kendiliğinden devam |
| Donma (görüntü akmıyor) | Fark edilmez | 10 sn ilerleme yoksa otomatik kurtarma |
| Bozuk ayar / önbellek | "Verileri temizle" ya da yeniden kurulum gerekir | Bozuk ayar silinir, uygulama varsayılanlarla açılır |
| Reklam, analitik, izleme | Var | **Yok.** İnternet izni sadece yayın için kullanılır. |
| Arka plan servisi | WorkManager, Firebase vb. | **Yok.** Uygulama kapalıyken hiçbir şey çalışmaz. |
| APK boyutu | Genelde 10–40 MB | **0,64 MB** (release) |
| Kaynak kodu | Kapalı | **Açık (MIT)** |

---

## Ölçülen sonuçlar

Aşağıdaki sayılar düşük donanımlı gerçek bir cihazda ölçüldü: **HAVIT PJ205 PRO** projeksiyon cihazı, Android 9, MStar mt5862 (32-bit ARM), **820 MB RAM**, aynı anda en fazla 2 AVC decoder. Ayrıntılar [TEST_REPORT.md](TEST_REPORT.md) dosyasında.

| Ölçüm | Sonuç |
|---|---|
| Soğuk açılış (10 deneme) | 1,89–2,07 sn |
| Açılıştan sonraki bellek (PSS) | ~47 MB |
| Kanal değiştirme süresi | 23 kanalın çoğunda **0,6–1,3 sn** |
| 20 kez Home'a çıkıp dönme | Bellek 88 → 76 MB, artmıyor |
| 120 hızlı kanal değişimi | Çökme yok, doğru kanalda kalıyor |
| 30 hızlı kanal değişiminden sonra decoder sayısı | **1** |
| Wi-Fi 45 sn kesilince | 11 sn içinde "kullanılamıyor", internet gelince 1,2 sn'de kendiliğinden başlıyor |
| Bozuk ayar dosyası | Açılış etkilenmiyor |
| Çökme / ANR | 0 |

---

## Özellikler

- **Açılır açılmaz yayın:** Splash ekranı yok. Son izlenen kanal hemen başlar.
- **Tam ekran video:** Ekranda başka bir şey yok. Kanal değişince numara, ad ve durum birkaç saniye görünür, sonra kaybolur.
- **Kanal listesi:** ▲/▼ ya da OK ile açılır. İzlenen kanal ▶, favoriler ★ ile işaretlidir.
- **Numarayla kanal değiştirme:** Kumandada `1`, `4` yazınca 14. kanala geçer.
- **Favoriler:** Listede OK'a uzun basmak favori ekler veya çıkarır.
- **Air-mouse kumanda desteği:** Fare imleci olan kumandalarda da liste düzgün çalışır.
- **Otomatik kurtarma:** Yayın koparsa ya da donarsa kanal kendiliğinden yeniden denenir, gerekirse yedek adrese geçilir.
- **İnternet geri gelince devam:** Bağlantı kopup gelince yayın kendiliğinden başlar.
- **Uyku ve uyanma:** TV uykuya geçince oynatıcı kapanır, uyanınca kaldığı kanaldan devam eder.
- **23 kanal:** Ulusal kanallar, haber kanalları ve spor kanalları (TRT Spor, TRT Spor Yıldız, A Spor, HT Spor, TJK TV).

Bilerek eklenmeyenler: reklam, analitik, hesap/giriş, EPG, kayıt, internet üzerinden kanal listesi indirme. Bunların hepsi uygulamayı büyütür ve kırılgan yapar. Ayrıntı [SSS](#sss) bölümünde.

---

## Kurulum

### Hazır APK ile

1. Projeyi derle (aşağıda) ya da yayınlanmış bir sürüm varsa APK'yı [Releases](https://github.com/kadirtutenn/minihome-tv/releases) sayfasından indir.
2. TV'de **Ayarlar → Cihaz tercihleri → Hakkında → Yapı numarası** satırına 7 kez basıp geliştirici seçeneklerini aç, **USB/ağ hata ayıklama**yı etkinleştir.
3. Bilgisayardan yükle:

```bash
adb connect <TV_IP_ADRESI>:5555
```

```bash
adb install -r app-debug.apk
```

Uygulama TV'nin uygulama listesinde **MiniHome TV** adıyla görünür. Android TV ana ekranında banner'ı da vardır.

### Kaynaktan derleme

Gereksinimler: **JDK 17+** (21 ile denendi) ve **Android SDK 36**. Gradle wrapper, Gradle 9.8.0'ı kendisi indirir. AGP 9.4.1 kullanılıyor.

```bash
git clone https://github.com/kadirtutenn/minihome-tv.git
```

```bash
cd minihome-tv && ./gradlew clean assembleDebug
```

APK `app/build/outputs/apk/debug/app-debug.apk` konumunda oluşur (~3,9 MB).

| Komut | Çıktı |
|---|---|
| `./gradlew assembleDebug` | Debug APK, ~3,9 MB |
| `./gradlew assembleRelease` | R8 ile küçültülmüş, **imzasız** release APK, ~0,64 MB. Kurmak için kendi anahtarınla imzalaman gerekir. |
| `./gradlew testDebugUnitTest` | Kanal listesi doğrulama testleri |

---

## Kumanda

| Tuş | Tam ekranda | Kanal listesi açıkken |
|---|---|---|
| ▲ / ▼ | Kanal listesini açar, imleç bir üst/alt kanalda | İmleci kaydırır |
| CH+ / CH− | Doğrudan sonraki/önceki kanal | İmleci kaydırır |
| OK | Kanal listesini açar | Seçili kanala geçer |
| OK (uzun basış) | — | Favori ekler/çıkarır (★) |
| 0–9 | Numarayla kanal seçer (1,5 sn bekler) | — |
| ◄ ► / INFO | Kanal bilgisini gösterir | — |
| PAGE ▲/▼ | — | 8 satır kaydırır |
| BACK | Uygulamadan çıkar | Listeyi kapatır |

Liste 6 sn boyunca tuşa basılmazsa kendiliğinden kapanır.

---

## Kanal listesi

Kanallar [`app/src/main/assets/channels.json`](app/src/main/assets/channels.json) dosyasında. Biçim:

```json
{
  "id": "trtspor",
  "number": 14,
  "name": "TRT Spor",
  "url": "https://tv-trtspor1.medya.trt.com.tr/master.m3u8",
  "backup": ["https://trt.daioncdn.net/trtspor/master.m3u8?app=web&platform=trtspor"]
}
```

| Alan | Zorunlu | Açıklama |
|---|---|---|
| `id` | evet | Değişmeyen kimlik. Son izlenen kanal ve favoriler bununla saklanır. |
| `number` | evet | Kumandayla girilen numara. Benzersiz olmalı. |
| `name` | evet | Ekranda görünen ad |
| `url` | evet | HLS (`.m3u8`) adresi |
| `backup` | hayır | Ana adres çalışmazsa sırayla denenecek yedek adresler |

Geçersiz satırlar (adresi olmayan, numarası tekrar eden ya da adresi tekrar eden) uygulama açılırken sessizce atlanır. Hatalı bir JSON uygulamayı çökertmez, ekranda "Kanal listesi okunamadı" yazar.

Mevcut 23 kanal:

| No | Kanal | No | Kanal | No | Kanal |
|---|---|---|---|---|---|
| 1 | TRT 1 | 9 | NTV | 17 | HT Spor |
| 2 | ATV | 10 | A Haber | 18 | TJK TV |
| 3 | Kanal D | 11 | Halk TV | 19 | TRT Belgesel |
| 4 | Star TV | 12 | TGRT Haber | 20 | TRT Çocuk |
| 5 | NOW | 13 | 24 TV | 21 | TRT Müzik |
| 6 | TV8 | 14 | TRT Spor | 22 | TBMM TV |
| 7 | TRT 2 | 15 | TRT Spor Yıldız | 23 | TRT World |
| 8 | TRT Haber | 16 | A Spor | | |

Yayıncılar adreslerini zaman zaman değiştirir. Bir kanal açılmazsa önce adresi tarayıcıda ya da `curl` ile kontrol et, sonra JSON'u güncelleyip PR gönder.

---

## Kendi projende kullanmak

Kod MIT lisanslı. Kopyalayabilir, değiştirebilir, ticari projelerde kullanabilirsin. Tek şart lisans metnini korumak.

Sık karşılaşılan kullanım şekilleri:

- **Başka bir ülkenin kanalları:** Sadece `channels.json` dosyasını değiştir, `strings.xml` içindeki metinleri çevir. Kodda Türkiye'ye özel bir şey yok.
- **Otel / kafe / bekleme salonu ekranı:** Kanal listesini kısalt, `BACK` tuşunun uygulamayı kapatmasını engellemek için `handleBack()` fonksiyonunu değiştir.
- **Kendi uygulamanda sadece oynatıcı:** [`PlayerController.kt`](app/src/main/java/com/minihome/tv/PlayerController.kt) tek başına kullanılabilir. Bir `SurfaceView` ve bir durum callback'i alır. Kurtarma ve bekçi mantığı içindedir.
- **Paket adını değiştirmek:** `app/build.gradle.kts` içindeki `namespace` ve `applicationId` değerlerini değiştir, `com/minihome/tv` klasörünü taşı.

`PlayerController` kullanım örneği:

```kotlin
val player = PlayerController(applicationContext, surfaceView) { status ->
    // LOADING, PLAYING, RECOVERING, UNAVAILABLE
}
player.play(Channel("trt1", 1, "TRT 1", "https://tv-trt1.medya.trt.com.tr/master.m3u8"))
// ...
player.release() // onStop içinde
```

---

## Mimari

Uygulama üç Kotlin dosyasından oluşur, toplam yaklaşık 590 satır.

```
MainActivity  ──►  ChannelRepository   (assets/channels.json + SharedPreferences)
     │
     └────────►  PlayerController  ──►  tek ExoPlayer (HlsMediaSource → SurfaceView)
```

| Dosya | Görevi |
|---|---|
| [`MainActivity.kt`](app/src/main/java/com/minihome/tv/MainActivity.kt) | Yaşam döngüsü, kumanda tuşları, kanal bilgisi, kanal listesi, internet takibi |
| [`PlayerController.kt`](app/src/main/java/com/minihome/tv/PlayerController.kt) | Tek ExoPlayer, kurtarma sırası, donma bekçisi, görüntü oranı |
| [`ChannelRepository.kt`](app/src/main/java/com/minihome/tv/ChannelRepository.kt) | `Channel` modeli, JSON okuma, son kanal ve favoriler |
| [`channels.json`](app/src/main/assets/channels.json) | Kanal listesi |

Bağımlılıklar sadece `media3-exoplayer` ve `media3-exoplayer-hls`. AppCompat, media3-ui, Jetpack Compose, reklam, analitik, WorkManager ve Room yok. Manifest'te servis, receiver ya da provider yok.

---

## Kararlılık nasıl sağlanıyor

### Yaşam döngüsü

- Oynatıcı `onStart` içinde kurulur, `onStop` içinde **release** edilir. Home tuşu, TV uykusu ve üstte açılan başka bir uygulama hep bu yoldan geçer. Arka planda decoder, ağ bağlantısı ya da zamanlayıcı kalmaz.
- Herhangi bir anda en fazla **bir** ExoPlayer vardır. Yeni oynatıcı gerektiğinde önce eskisi kapatılır.
- `singleTask` ve `configChanges` sayesinde Activity yeniden yaratılmaz ve ikinci bir kopyası açılmaz.

### Kurtarma sırası

Kanal değişimi aynı oynatıcıda `setMediaItem → prepare → play` ile yapılır. Hata olursa her kanal için sınırlı bir sıra izlenir:

```
ana adres → ana adres tekrar → yeni oynatıcıyla ana adres → yedek 1 → yedek 2 → "kullanılamıyor"
```

- **Tetikleyiciler:** oynatıcı hatası, canlı yayının bitmesi, oynatma sırasında **10 sn**, ilk görüntü için **12 sn** ilerleme olmaması.
- **Decoder hatası** gelirse bir sonraki deneme her durumda yeni bir oynatıcıyla yapılır.
- **Canlı pencerenin gerisinde kalma** hatasında canlı uca atlanır. Bu bir deneme sayılmaz.
- Sıra yalnızca **30 sn kesintisiz oynatmadan sonra** sıfırlanır. Böylece sonsuz deneme döngüsü oluşmaz.
- İnternet kesilince ekranda "İnternet bağlantısı yok" yazar. Bağlantı gelince kanal kendiliğinden başlar.

### Bellek ve hız

- Tamponlar küçük tutulur: 10–30 sn, başlamak için 1 sn. Canlı yayında dakikalarca tampon bir işe yaramaz, sadece RAM yer.
- `DefaultMediaSourceFactory` yerine doğrudan `HlsMediaSource.Factory` kullanılır. DASH, SmoothStreaming ve RTSP kodu R8 ile tamamen atılır.
- Görüntü düz bir `SurfaceView` üzerine çizilir, en-boy oranı yaklaşık 10 satır kodla ayarlanır. `media3-ui` gerekmez.

### Kalıcı durum

Sadece **son izlenen kanal** ve **favoriler** saklanır. Her okuma hata yakalamayla korunur. Ayar dosyası bozuksa ya da değerin tipi yanlışsa ayarlar silinir ve uygulama varsayılanlarla açılır. Uygulamanın açılması hiçbir durumda kayıtlı ayarlara bağlı değildir.

### Kumanda

Kanal listesi odak (focus) almaz, imleci uygulama kendisi yönetir. Air-mouse kumandalar Android'i sürekli dokunmatik moda soktuğu için, odak kullanan listelerde her yön tuşu basışı sadece bu moddan çıkmaya harcanır ve imleç hareket etmez. MiniHome TV'de bu sorun yok.

---

## Test

```bash
./gradlew testDebugUnitTest
```

[`tools/smoke.sh`](tools/smoke.sh) bağlı bir cihazda ya da emülatörde adb ile senaryo testleri çalıştırır:

| Komut | Ne yapar |
|---|---|
| `tools/smoke.sh cold` | 10 soğuk açılış, açılış süresi ve bellek |
| `tools/smoke.sh home` | 20 kez Home'a çıkıp geri dönme, bellek ve Activity sayısı |
| `tools/smoke.sh rapid` | 120 hızlı kanal değişimi |
| `tools/smoke.sh sleep` | 5 uyku/uyanma döngüsü |
| `tools/smoke.sh prefs` | Bozuk ayar dosyasıyla açılış |
| `tools/smoke.sh net` | İnternet kesip geri açma (Wi-Fi ile bağlı cihazda adb bağlantısını da keser) |
| `tools/smoke.sh zap` | Bütün kanalları tek tek açıp oynadıklarını kontrol etme |
| `tools/smoke.sh long` | 35 dk oynatma, 5 dk'da bir bellek ölçümü |
| `tools/smoke.sh all` | `long` dışındakilerin hepsi |

Birden fazla cihaz bağlıysa önce `export ANDROID_SERIAL=<cihaz>` ile hedefi seç.

---

## Katkı

Katkıya açığız. En değerli katkılar:

1. **Kanal adresi güncellemeleri:** Açılmayan bir kanal için çalışan resmi adresi bul, `channels.json` dosyasını güncelle, PR gönder.
2. **Farklı cihazlarda test:** `tools/smoke.sh all` sonucunu cihaz modeliyle birlikte bir issue olarak paylaş.
3. **Hata düzeltmeleri:** Sorunu tekrar üretecek adımları ve `adb logcat -s PlayerController` çıktısını ekle.

Kanal eklerken kurallar:

- Sadece yayıncının **kendi** sitesinde ya da CDN'inde herkese açık sunduğu **ücretsiz** yayınlar.
- Ücretli kanallar (beIN Sports, S Sport, Tivibu Spor vb.), DRM'li yayınlar ve izinsiz yeniden yayın (restream) adresleri **kabul edilmez**.
- Süresi dolacak imzalı adresler (`token=`, `e=`, `st=` gibi) eklenmez, çünkü birkaç saat sonra çalışmaz.

Kod değişikliklerinde projenin ruhunu koru: yeni bağımlılık eklemeden önce iki kez düşün. Uygulamanın asıl değeri küçük ve kararlı olması.

---

## SSS

**Neden EPG (yayın akışı) yok?**
EPG için sürekli güncellenen bir veri kaynağı ve arka planda indirme gerekir. Bu da uygulamayı büyütür ve yeni bir hata kaynağı ekler. Bu proje bilerek tek bir işe odaklanıyor: kanalı hızlı ve kararlı açmak.

**Kendi M3U listemi yükleyebilir miyim?**
Uygulama içinden hayır. Kanal listesi APK'nın içinde. `channels.json` dosyasını düzenleyip yeniden derleyebilirsin. İnternetten liste indirme özelliği bilerek yok: uzaktaki bir listenin bozulması uygulamanın açılmasını engellememeli.

**Telefonda çalışır mı?**
Çalışır, ama kumanda için tasarlandı. Dokunmatik kontrol yok, sadece listede satıra dokunmak çalışır.

**Neden bazı kanallar geç açılıyor?**
NOW ve A Spor gibi bazı kanallar 10 saniyelik yayın parçaları kullanıyor. Düşük donanımlı cihazlarda ilk görüntü 8–9 sn sürebiliyor. Bekçi bu kanallara 12 sn tanır.

**Bir kanal "kullanılamıyor" diyor.**
Önce internet bağlantını kontrol et. Bağlantı varsa yayıncı adresi değiştirmiş olabilir. OK ile listeyi açıp kanalı yeniden seçmek baştan dener. Sorun sürerse bir issue aç.

**Yurt dışından çalışır mı?**
Bazı yayıncılar yayını Türkiye dışına kapatır (coğrafi kısıtlama). Bu kanallar yurt dışında açılmayabilir.

---

## Yasal not

MiniHome TV **hiçbir içerik barındırmaz ve yeniden yayınlamaz.** Sadece yayıncıların kendi internet sitelerinde herkese açık sundukları ücretsiz yayınları oynatır. Bütün yayınların ve markaların hakları ilgili yayıncılara aittir. Bu proje hiçbir yayıncıyla bağlantılı değildir.

Bir yayıncıysan ve kanalının listeden çıkarılmasını istiyorsan bir issue aç, hemen kaldırılır.

---

## Lisans

[MIT](LICENSE) © 2026 Abdulkadir Tüten
