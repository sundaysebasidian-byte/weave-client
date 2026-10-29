using System.Buffers.Binary;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Weave.Windows.Core;

[JsonPolymorphic(TypeDiscriminatorPropertyName = "operation")]
[JsonDerivedType(typeof(BrokerStart), "start")]
[JsonDerivedType(typeof(BrokerStop), "stop")]
[JsonDerivedType(typeof(BrokerStatus), "status")]
[JsonDerivedType(typeof(BrokerResolve), "resolve")]
[JsonDerivedType(typeof(BrokerProbe), "probe")]
[JsonDerivedType(typeof(BrokerTraffic), "traffic")]
public abstract record BrokerRequest(long Id);
public sealed record BrokerStart(long Id, BrokerProfile Profile) : BrokerRequest(Id);
public sealed record BrokerStop(long Id) : BrokerRequest(Id);
public sealed record BrokerStatus(long Id) : BrokerRequest(Id);
public sealed record BrokerResolve(long Id, string Host) : BrokerRequest(Id);
public sealed record BrokerProbe(long Id, string NodeName) : BrokerRequest(Id);
public sealed record BrokerTraffic(long Id) : BrokerRequest(Id);
public sealed record BrokerReply(long Id, string? Error = null, BrokerRuntimeInfo? Runtime = null,
    string[]? Addresses = null, int? Delay = null, long? Down = null, long? Up = null, bool? Ready = null);

public static class BrokerWire
{
    public const int Version = 1;
    public const int MaxFrameBytes = 40 * 1024 * 1024;
    private static readonly JsonSerializerOptions Json = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
        MaxDepth = 32,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
        AllowDuplicateProperties = false,
        RespectRequiredConstructorParameters = true,
        RespectNullableAnnotations = true,
        Converters = { new JsonStringEnumConverter(allowIntegerValues: false) },
    };

    public static async Task<T?> ReadAsync<T>(Stream stream, CancellationToken token) where T : class
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
        var header = new byte[8];
        if (await stream.ReadAsync(header.AsMemory(0, 1), timeout.Token).ConfigureAwait(false) == 0) return null;
        // An authenticated idle connection is its owner's lease. Only a partially
        // transmitted frame has a deadline; hiding the GUI must not stop the tunnel.
        timeout.CancelAfter(TimeSpan.FromSeconds(30));
        await stream.ReadExactlyAsync(header.AsMemory(1), timeout.Token).ConfigureAwait(false);
        var version = BinaryPrimitives.ReadInt32BigEndian(header);
        var length = BinaryPrimitives.ReadInt32BigEndian(header.AsSpan(4));
        if (version != Version || length is <= 0 or > MaxFrameBytes) throw new InvalidDataException("Invalid service message frame.");
        var payload = new byte[length];
        await stream.ReadExactlyAsync(payload, timeout.Token).ConfigureAwait(false);
        return JsonSerializer.Deserialize<T>(payload, Json) ?? throw new InvalidDataException("Empty service message.");
    }

    public static async Task WriteAsync<T>(Stream stream, T value, CancellationToken token) where T : class
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
        timeout.CancelAfter(TimeSpan.FromSeconds(30));
        // Bound the serialized destination before sending any header or payload.
        using var payload = new BoundedBuffer();
        await JsonSerializer.SerializeAsync(payload, value, Json, timeout.Token).ConfigureAwait(false);
        var header = new byte[8];
        BinaryPrimitives.WriteInt32BigEndian(header, Version);
        BinaryPrimitives.WriteInt32BigEndian(header.AsSpan(4), checked((int)payload.Length));
        await stream.WriteAsync(header, timeout.Token).ConfigureAwait(false);
        await stream.WriteAsync(payload.GetBuffer().AsMemory(0, checked((int)payload.Length)), timeout.Token).ConfigureAwait(false);
        await stream.FlushAsync(timeout.Token).ConfigureAwait(false);
    }

    private sealed class BoundedBuffer : MemoryStream
    {
        private void Check(int count)
        {
            if (Position + count > MaxFrameBytes) throw new InvalidDataException("Service message exceeds the size limit.");
        }
        public override void Write(byte[] buffer, int offset, int count) { Check(count); base.Write(buffer, offset, count); }
        public override void Write(ReadOnlySpan<byte> buffer) { Check(buffer.Length); base.Write(buffer); }
        public override ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken cancellationToken = default)
        { cancellationToken.ThrowIfCancellationRequested(); Write(buffer.Span); return ValueTask.CompletedTask; }
        public override Task WriteAsync(byte[] buffer, int offset, int count, CancellationToken cancellationToken)
        { cancellationToken.ThrowIfCancellationRequested(); Write(buffer, offset, count); return Task.CompletedTask; }
    }
}
