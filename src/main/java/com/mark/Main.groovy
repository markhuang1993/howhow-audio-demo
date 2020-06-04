package com.mark

import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import java.util.regex.Pattern

class Main {

    private static final List<Chinese> CHINESE_LIST
    private static final Map<String, String> AUDIO_MAP

    static {
        def tsi = Main.class.getClassLoader().getResourceAsStream('tsi.src').text

        CHINESE_LIST = tsi.split('\n')
                .findAll { it != '' && !it.matches('^#') }
                .collect {
                    def sp = it.split(' ')
                    new Chinese(word: sp[0], chewing: sp[2..-1].join(' '))
                }
                .sort { a, b ->
                    Integer.compare(a.word.length(), b.word.length()) * -1
                }

        def audioMapTxt = Main.class.getClassLoader().getResourceAsStream('audio_map.txt').text
        def audioMap = new HashMap()
        audioMapTxt.split('\r?\n').findAll { it != '' }.forEach {
            def sp = it.split(' ')
            audioMap.put(sp[1], sp[0])
        }
        AUDIO_MAP = audioMap
    }


    static void main(String[] args) {
        def chewing = toChewing('八拔把爸伯伯')

        def audios = getAudios(chewing)

        println mergeAudios(audios.collect { AudioSystem.getAudioInputStream(it) }, "wav", AudioFileFormat.Type.WAVE)
    }

    private static File[] getAudios(String chewing) {
        List<File> result = new ArrayList<>()

        Pattern p = Pattern.compile('^(.*?)([ˊˇˋ˙])?$')
        chewing.split(' ').each {
            def matcher = p.matcher(it)
            if (matcher.find()) {
                def chewingWord = matcher.group(1)
                def chewingSound = matcher.group(2) ?: ''

                def soundIdx
                if (chewingSound == 'ˊ') {
                    soundIdx = 1
                } else if (chewingSound == 'ˇ') {
                    soundIdx = 2
                } else if (chewingSound == 'ˋ') {
                    soundIdx = 3
                } else if (chewingSound == '˙') {
                    soundIdx = 4
                } else {
                    soundIdx = 0
                }

                def fileName = AUDIO_MAP.get(chewingWord) + '-' + soundIdx + '.wav'

                def filePath = Main.class.getClassLoader().getResource("audio/${fileName}").getFile()
                if (filePath == null) {
                    throw new RuntimeException("Audio file:${fileName} not found.")
                }
                result.add(new File(filePath))
                return
            }
            throw new RuntimeException("Chewing: ${chewing} not found matched audio.")
        }

        result
    }

    private static String toChewing(String str) {
        String result = str
        CHINESE_LIST.forEach {
            if (result.indexOf(it.word) != -1) {
                result = result.replace(it.word, it.chewing + ' ')
            }
        }
        result
    }

    private static class Chinese {
        String word
        String chewing
    }

    static File mergeAudios(List<AudioInputStream> audioInputStreams, String fileType, AudioFileFormat.Type audioType) {
        try {
            UUID randomUUID = UUID.randomUUID()
            File resultFile = new File(randomUUID.toString().concat('.').concat(fileType))
            AudioInputStream appendedFiles = null
            if (audioInputStreams.size() == 0) {
                return null
            }
            if (audioInputStreams.size() == 1) {
                AudioSystem.write(audioInputStreams.get(0), audioType, resultFile)
                return resultFile
            }
            for (int i = 0; i < audioInputStreams.size() - 1; i++) {
                def audio = audioInputStreams.get(i)
                def nextAudio = audioInputStreams.get(i + 1)
                if (i == 0) {
                    appendedFiles = new AudioInputStream(
                            new SequenceInputStream(audio, nextAudio), audio.getFormat(),
                            audio.getFrameLength() + nextAudio.getFrameLength())
                    continue
                }

                appendedFiles = new AudioInputStream(
                        new SequenceInputStream(appendedFiles, nextAudio), appendedFiles.getFormat(),
                        appendedFiles.getFrameLength() + nextAudio.getFrameLength()
                )
            }

            AudioSystem.write(appendedFiles, audioType, resultFile)
            return resultFile
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            audioInputStreams.forEach {
                try {
                    if (it != null) {
                        it.close()
                    }
                } catch (Exception e) {
                    e.printStackTrace()
                }
            }
        }
        null
    }
}
