package ch.srgssr.pillarbox.backend.integrationlayer

import ch.srgssr.pillarbox.backend.test.IntegrationLayerFixtures
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

private fun decode(fixture: String): MediaComposition = json.decodeFromString(fixture)

class MediaCompositionMapperTest :
  ShouldSpec({

    context("VOD composition") {
      val media = decode(IntegrationLayerFixtures.vodComposition).toMedia().shouldNotBeNull()

      should("map the main chapter metadata") {
        media.id shouldBe "urn:rsi:video:3845234"
        media.metadata.title shouldBe "Telegiornale flash"
        media.metadata.subtitle shouldBe "Telegiornale"
        media.metadata.posterUrl shouldBe "https://il.rsi.ch/rsi-api/resize/image/v2/WEBVISUAL/3878974"
        media.metadata.seasonNumber shouldBe 2
        media.metadata.episodeNumber shouldBe 7
        media.tags shouldBe emptyList()
      }

      should("fall back to the lead when the description is blank") {
        media.metadata.description shouldBe "Le notizie di mezza giornata"
      }

      should("emit one source per streaming method, preferring HD variants") {
        media.sources shouldHaveSize 2
        media.sources.map { it.url } shouldContainExactly
          listOf(
            "https://rsivod.akamaized.net/out/v1/telegiornale/index.m3u8",
            "https://rsivod.akamaized.net/telegiornale/video.mp4",
          )
        media.sources.map { it.type }.toSet() shouldBe setOf("ON-DEMAND")
      }

      should("derive the mime type when the resource does not provide one") {
        media.sources.first().mimeType shouldBe "application/x-mpegURL"
        media.sources.last().mimeType shouldBe "video/mp4"
      }

      should("map segments to chapters, skipping the requested media itself") {
        val chapters = media.metadata.chapters.shouldNotBeNull()
        chapters shouldHaveSize 2
        chapters.first().identifier shouldBe "urn:rsi:video:3878648"
        chapters.first().title shouldBe "Nuovi attacchi USA contro l'Iran"
        chapters.first().startTime shouldBe 46600
        chapters.first().endTime shouldBe 156256
        chapters.first().posterUrl shouldBe "https://il.rsi.ch/rsi-api/resize/image/v2/WEBVISUAL/3878975"
      }

      should("keep only VTT subtitle tracks") {
        val subtitles = media.metadata.subtitles.shouldNotBeNull()
        subtitles shouldHaveSize 1
        subtitles.first().label shouldBe "Italiano"
        subtitles.first().kind shouldBe "captions"
        subtitles.first().language shouldBe "it"
        subtitles.first().url shouldBe
          "https://rsi-subtitles.s3.eu-central-1.amazonaws.com/subt_web/rsi/production/2026/telegiornale.vtt"
      }
    }

    context("live composition with DRM") {
      val media = decode(IntegrationLayerFixtures.liveDrmComposition).toMedia().shouldNotBeNull()

      should("prefer the DVR variant of each streaming method") {
        media.sources shouldHaveSize 2
        media.sources.map { it.type }.toSet() shouldBe setOf("DVR")
        media.sources.map { it.url } shouldContainExactly
          listOf(
            "https://lsvs-rts1-d.akamaized.net/out/v1/82ab0e39500a47a3b7ac54626d5399b5/index.m3u8?dw=7201",
            "https://lsvs-rts1-d.akamaized.net/out/v1/9bd2958c2c564691b8dd08fc652d4d6e/index.mpd?dw=7201",
          )
      }

      should("map DRM systems to their key system identifiers") {
        val hls = media.sources.first()
        hls.drmConfigs shouldHaveSize 1
        hls.drmConfigs.first().keySystem shouldBe "com.apple.fps"
        hls.drmConfigs.first().certificateUrl shouldBe
          "https://srg.live.ott.irdeto.com/licenseServer/streaming/v1/SRG/getcertificate?applicationId=live"

        val dash = media.sources.last()
        dash.drmConfigs.map { it.keySystem } shouldContainExactly
          listOf("com.widevine.alpha", "com.microsoft.playready")
      }

      should("use the show title as subtitle and leave series fields empty") {
        media.metadata.subtitle shouldBe "RTS 1"
        media.metadata.seasonNumber.shouldBeNull()
        media.metadata.episodeNumber.shouldBeNull()
        media.metadata.chapters.shouldBeNull()
        media.metadata.subtitles.shouldBeNull()
      }
    }

    context("degenerate compositions") {
      should("return null when the composition has no chapter") {
        MediaComposition(chapterUrn = "urn:rts:video:1").toMedia().shouldBeNull()
      }

      should("fall back to the first chapter when none matches the requested URN") {
        val composition =
          MediaComposition(
            chapterUrn = "urn:rts:video:missing",
            chapterList = listOf(CompositionChapter(urn = "urn:rts:video:1", title = "Fallback")),
          )

        val media = composition.toMedia().shouldNotBeNull()
        media.id shouldBe "urn:rts:video:1"
        media.metadata.title shouldBe "Fallback"
      }

      should("skip unsupported streaming methods and unknown DRM types") {
        val composition =
          MediaComposition(
            chapterUrn = "urn:rts:video:1",
            chapterList =
              listOf(
                CompositionChapter(
                  urn = "urn:rts:video:1",
                  title = "Streams",
                  resourceList =
                    listOf(
                      Resource(url = "https://stream.com/legacy.rtmp", streaming = "UNKNOWN"),
                      Resource(
                        url = "https://stream.com/index.m3u8",
                        streaming = "HLS",
                        drmList = listOf(Drm(type = "CUSTOM", licenseUrl = "https://license.com")),
                      ),
                    ),
                ),
              ),
          )

        val media = composition.toMedia().shouldNotBeNull()
        media.sources shouldHaveSize 1
        media.sources.first().url shouldBe "https://stream.com/index.m3u8"
        media.sources.first().drmConfigs shouldBe emptyList()
      }
    }
  })
