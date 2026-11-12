package voice.core.ui

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.datastore.VoiceDataStoreFactory

/** Whether the player shows the new view with synced captions and an EPUB reader. */
@SingleIn(AppScope::class)
class NewUiStore(store: DataStore<Boolean>) : DataStore<Boolean> by store {

  @Inject
  constructor(
    factory: VoiceDataStoreFactory,
  ) : this(
    factory.boolean("newUi", defaultValue = false),
  )
}
