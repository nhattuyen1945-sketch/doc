import SwiftUI

/// Màn hình trên điện thoại: tìm bài, phát, quản lý playlist và từ khóa "Tìm nhanh" cho CarPlay.
struct ContentView: View {
    @EnvironmentObject var store: LibraryStore
    @EnvironmentObject var player: PlayerManager

    @State private var query = ""
    @State private var results: [Track] = []
    @State private var loading = false
    @State private var error: String?
    @State private var tab = 0

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack {
                    TextField("Tìm trên YouTube", text: $query)
                        .textFieldStyle(.roundedBorder)
                        .submitLabel(.search)
                        .onSubmit { search(query) }
                    Button("Tìm") { search(query) }.buttonStyle(.borderedProminent)
                }.padding()

                Picker("", selection: $tab) {
                    Text("Kết quả").tag(0); Text("Playlist").tag(1)
                    Text("Gần đây").tag(2); Text("Tìm nhanh").tag(3)
                }.pickerStyle(.segmented).padding(.horizontal)

                if loading { ProgressView().padding(4) }
                if let msg = error ?? player.lastError {
                    Text(msg).font(.footnote).foregroundStyle(.red).padding(.horizontal)
                }

                switch tab {
                case 0: trackList(results, action: ("plus", store.addToPlaylist))
                case 1: trackList(store.playlist, action: ("trash", store.removeFromPlaylist))
                case 2: trackList(store.recents, action: ("plus", store.addToPlaylist))
                default: queryList
                }

                nowPlayingBar
            }
            .navigationTitle("Drive Player")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                NavigationLink { SettingsView() } label: { Image(systemName: "gearshape") }
            }
            .onAppear { if store.apiKey.isEmpty { error = SourceError.missingAPIKey.errorDescription } }
        }
    }

    private func search(_ q: String) {
        guard !q.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        tab = 0; loading = true; error = nil
        Task {
            do { results = try await player.source.search(q) } catch { self.error = error.localizedDescription }
            loading = false
        }
    }

    private func trackList(_ tracks: [Track], action: (String, (Track) -> Void)) -> some View {
        List {
            if tracks.isEmpty { Text("Trống").foregroundStyle(.secondary) }
            ForEach(Array(tracks.enumerated()), id: \.element.id) { i, t in
                HStack {
                    AsyncImage(url: t.artworkURL) { $0.resizable().scaledToFill() } placeholder: { Color.gray.opacity(0.3) }
                        .frame(width: 56, height: 42).clipShape(RoundedRectangle(cornerRadius: 4))
                    VStack(alignment: .leading) {
                        Text(t.title).lineLimit(2)
                        Text(t.artist).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button { action.1(t) } label: { Image(systemName: action.0) }.buttonStyle(.borderless)
                }
                .contentShape(Rectangle())
                .onTapGesture { player.play(tracks, startAt: i) }
            }
        }.listStyle(.plain)
    }

    private var queryList: some View {
        List {
            Section(footer: Text("CarPlay không có bàn phím, nên các từ khóa này hiện trong tab \"Tìm nhanh\" trên xe.")) {
                Button("+ Lưu \"\(query)\" thành từ khóa") { store.addQuery(query) }
                    .disabled(query.trimmingCharacters(in: .whitespaces).isEmpty)
                ForEach(store.savedQueries, id: \.self) { q in
                    Button(q) { query = q; search(q) }
                        .swipeActions { Button("Xóa", role: .destructive) { store.removeQuery(q) } }
                }
            }
        }
    }

    @ViewBuilder private var nowPlayingBar: some View {
        if let t = player.current {
            Divider()
            HStack {
                Text(t.title).lineLimit(1)
                Spacer()
                Button { player.previous() } label: { Image(systemName: "backward.fill") }
                Button { player.toggle() } label: { Image(systemName: player.isPlaying ? "pause.fill" : "play.fill") }
                Button { player.next() } label: { Image(systemName: "forward.fill") }
            }
            .font(.title3).padding()
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject var store: LibraryStore

    var body: some View {
        Form {
            Section(header: Text("YouTube Data API key"),
                    footer: Text("Lấy miễn phí tại console.cloud.google.com → APIs & Services → bật \"YouTube Data API v3\" → Credentials → Create API key. Mỗi ngày tìm được khoảng 100 lần.")) {
                TextField("AIza...", text: $store.apiKey)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
            }
        }.navigationTitle("Cài đặt")
    }
}
