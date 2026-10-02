# Test raporu — 2026-10-03

Komutlar `tools/smoke.sh <test>` içinde. Sonuçlar adb, logcat, `dumpsys meminfo` ve `dumpsys media.resource_manager` çıktılarından alındı.

## Cihazlar
- **Gerçek cihaz:** HAVIT PJ205 PRO projeksiyon cihazı. Android 9 (API 28), MStar mt5862, 32-bit ARM, 820 MB RAM, Wi-Fi bağlantısı. AVC decoder için `concurrent-instances max=2`.
- **Emülatör:** Android 16 (API 36.1) telefon imajı, yatay ekran. İlk geliştirme testleri burada yapıldı.

## Sonuçlar (gerçek cihaz)

| Test | Ne yapıldı | Sonuç |
|---|---|---|
| Soğuk açılış | 10× force-stop + `am start -W` | Açılış 1,89–2,07 sn. TRT 1 kendiliğinden başladı. PSS 47 MB. Çökme yok. |
| Tüm kanallar | 20 kanal ▲ tuşuyla gezildi | **20/20 açıldı.** Görüntünün gelmesi 14 kanalda 0,6–1,3 sn, ATV ve 4 TRT kanalında 3,6–4,3 sn, NOW'da 8,6 sn sürdü. |
| Home'dan tekrar açma | 20× HOME ve geri açma | PSS 88 → 76 MB, artmıyor. `Activities: 1`. |
| Hızlı kanal değiştirme | 60× CH+ (150 ms arayla) + 60× ▼ | Doğru kanalda kaldı. PSS 93 MB. `Activities: 1`. |
| Decoder sızıntısı | 30× hızlı CH+ sonrası `media.resource_manager` | Uygulamada **tek** `video-codec` kaydı var. Eski process'lerden kalan istemci yok. |
| Uyku/uyanma | 5× SLEEP/WAKEUP | Her uyanışta oynatıcı yeniden kuruldu ve TRT 1 PLAYING durumuna geçti. PSS 76 MB. |
| Bozuk tercih dosyası | Bozuk XML, ardından yanlış tipte `last_channel` | Her iki durumda da açıldı. Yanlış tipte değer görülünce tercihler sıfırlandı ve TRT 1 başladı. |
| Ağ kesintisi | Wi-Fi TV'nin içinden 45 sn kapatıldı | Kurtarma sırası 11 sn içinde tükendi ve "kullanılamıyor" gösterildi, sonsuz deneme yok. Wi-Fi gelince 1,2 sn içinde kendiliğinden PLAYING oldu. |
| Erişilemeyen yayın | Ağ testiyle aynı yol | birincil → tekrar → yeni oynatıcı → UNAVAILABLE, toplam 3 deneme. |
| Uzun oynatma | — | koşulmadı, aşağıya bakın |

Testlerin hiçbirinde çökme, ANR ya da kilitlenme görülmedi (`logcat -b crash` boş).

## Uzun oynatma
Kullanıcının isteğiyle 35 dakikalık test koşulmadı. Elde olan veriler: emülatörde yaklaşık 15 dk TRT 1 oynatıldı, PSS 115 → 123 → 139 → 127 MB arasında gidip geldi ve tek bir kurtarma adımı bile gerekmedi. Gerçek cihazda ise yaklaşık 5 dk oynatma sırasında PSS 44 MB ölçüldü.

## Spor kanalları (sonradan eklendi)
A Spor, HT Spor ve TJK TV gerçek cihazda açılıp oynadı. A Spor'un açılışı yaklaşık 9 sn sürdü; NOW ile aynı CDN'de. TRT Spor ve TRT Spor Yıldız'a TRT'nin ikinci CDN'i yedek adres olarak eklendi; bu adresler curl ile kontrol edildi. TV8,5 eklenmedi, çünkü süresiz ve herkese açık resmi bir adresi yok. S Sport gibi yalnızca korsan yeniden yayın adresi bulunan kanallar bilerek eklenmedi.

## Bu testler sonucunda yapılan düzeltme
- NOW kanalının ilk görüntüsü bu cihazda yaklaşık 9 sn sürüyor (10 sn'lik segmentler). Bu süre 10 sn'lik takılma sınırını gereksiz yere tetikleyebiliyordu. İlk görüntü için sınır **12 sn** yapıldı. Oynatma başladıktan sonraki takılma sınırı 10 sn olarak kaldı.

## Bilinen sınırlar
- Emülatörde NOW yayınında görülen görüntü kayması, kullanıcının TV'de gözle yaptığı kontrolde görülmedi. Bu yalnızca emülatöre özgü bir sorun.
- KEYCODE_SLEEP, kumandadaki gerçek güç tuşunun yerine kullanıldı. Projeksiyon cihazının fiziksel olarak kapatılıp açılması ayrıca denenmedi.
- Bazı yayın adreslerinde yayıncıya ait `app=`/`sid=` parametreleri var. Bunlar ileride değişebilir, o zaman `channels.json` güncellenmeli.
