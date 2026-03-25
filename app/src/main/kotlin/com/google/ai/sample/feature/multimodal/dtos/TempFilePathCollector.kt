package com.google.ai.sample.feature.multimodal.dtos

object TempFilePathCollector {
    fun collect(inputContentDto: ContentDto, chatHistoryDtos: List<ContentDto>): ArrayList<String> {
        val tempFilePaths = ArrayList<String>()

        inputContentDto.parts.forEach { partDto ->
            if (partDto is ImagePartDto) {
                tempFilePaths.add(partDto.imageFilePath)
            }
        }

        chatHistoryDtos.forEach { contentDto ->
            contentDto.parts.forEach { partDto ->
                if (partDto is ImagePartDto) {
                    tempFilePaths.add(partDto.imageFilePath)
                }
            }
        }

        return tempFilePaths
    }
}
