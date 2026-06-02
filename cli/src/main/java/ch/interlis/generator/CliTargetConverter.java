package ch.interlis.generator;

import picocli.CommandLine.ITypeConverter;

final class CliTargetConverter implements ITypeConverter<CliTarget> {

    @Override
    public CliTarget convert(String value) {
        return CliTarget.fromCliName(value);
    }
}
