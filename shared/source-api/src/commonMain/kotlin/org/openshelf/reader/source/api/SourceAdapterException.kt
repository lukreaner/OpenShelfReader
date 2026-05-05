package org.openshelf.reader.source.api

class SourceAdapterException(
    val error: SourceError,
) : RuntimeException(error.toString())
