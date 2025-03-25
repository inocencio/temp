import sublime
import sublime_plugin


class AutoReplaceCommand(sublime_plugin.TextCommand):
    def run(self, edit, replace_with, region_start, region_end):
        # Substitui o texto selecionado pela palavra desejada
        region = sublime.Region(region_start, region_end)
        self.view.replace(edit, region, replace_with)

        # Localize o marcador '$end'
        if "$end" in replace_with:
            # Obter a posição do marcador $end
            end_pos = replace_with.find("$end") + region_start

            # Remover o marcador $end do texto na view
            cleaned_text = replace_with.replace("$end", "")
            self.view.replace(edit, sublime.Region(region_start, region_start + len(replace_with)), cleaned_text)

            # Posicionar o cursor onde estava $end
            self.view.sel().clear()
            self.view.sel().add(sublime.Region(end_pos, end_pos))


class ReplaceListener(sublime_plugin.EventListener):
    # Lista de substituições definidas como 'texto_digitado': 'substituição'
    substitutions = {
        "qw": "let",
        "qq": "let mut",
        "maa": "macro_rules!",
        "sop": "println!($end);"  # Substituição com marcador $end
    }

    def on_modified(self, view):
        # Verifica se o tipo de arquivo é Rust
        if not view.match_selector(0, "source.rust"):
            return

        # Verifica cada cursor/seleção no buffer
        for region in view.sel():
            if region.empty():
                pos = region.begin()

                # Para cada substituição configurada
                for trigger, replacement in self.substitutions.items():
                    # Verifica se os últimos caracteres correspondem ao gatilho
                    trigger_len = len(trigger)
                    if view.substr(sublime.Region(pos - trigger_len, pos)) == trigger:
                        # Substitui o texto diretamente
                        view.run_command(
                            "auto_replace",
                            {
                                "replace_with": replacement,
                                "region_start": pos - trigger_len,
                                "region_end": pos
                            }
                        )
