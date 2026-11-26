package info.nukoneko.android.nfcreader.model.reader

import android.nfc.tech.IsoDep
import android.nfc.tech.TagTechnology
import info.nukoneko.android.nfcreader.model.entity.NfcField

class IsoDepEmvReader : TechReader {
    override val techName: String = "android.nfc.tech.IsoDep"

    override fun read(tech: TagTechnology, context: TechReadContext) {
        val isoDep = tech as IsoDep
        context.addMetadata(listOf(NfcField("IsoDep.maxTransceiveLength", isoDep.maxTransceiveLength.toString())))
        readWithTransport(
            transport = ReadOnlyTransport { command -> isoDep.transceive(command) },
            maxTransceiveLength = isoDep.maxTransceiveLength,
            context = context,
        )
    }

    internal fun readWithTransport(transport: ReadOnlyTransport, maxTransceiveLength: Int, context: TechReadContext) {
        IsoDepEmvProtocol(maxTransceiveLength).read(context.budgetedTransport(transport, "ISO-DEP APDU"), context)
    }
}
