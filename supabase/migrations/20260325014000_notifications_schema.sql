-- Notifications schema for read, soft delete, and internal publishing.

CREATE OR REPLACE FUNCTION set_updated_at_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    customer_id UUID,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    message TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'INFO',
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    channel TEXT NOT NULL DEFAULT 'push',
    priority TEXT NOT NULL DEFAULT 'normal',
    is_read BOOLEAN NOT NULL DEFAULT false,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    image_url TEXT,
    action_label TEXT,
    action_route TEXT,
    read_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS customer_id UUID;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS title TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS body TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS message TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'INFO';
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS data JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS channel TEXT NOT NULL DEFAULT 'push';
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS priority TEXT NOT NULL DEFAULT 'normal';
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS is_read BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS image_url TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS action_label TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS action_route TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

UPDATE notifications
SET body = COALESCE(body, message, '')
WHERE body IS NULL;

UPDATE notifications
SET message = COALESCE(message, body, '')
WHERE message IS NULL;

UPDATE notifications
SET data = '{}'::jsonb
WHERE data IS NULL;

UPDATE notifications
SET channel = 'push'
WHERE channel IS NULL;

UPDATE notifications
SET priority = 'normal'
WHERE priority IS NULL;

UPDATE notifications
SET is_read = false
WHERE is_read IS NULL;

UPDATE notifications
SET sent_at = COALESCE(sent_at, created_at, now())
WHERE sent_at IS NULL;

UPDATE notifications
SET is_deleted = false
WHERE is_deleted IS NULL;

UPDATE notifications
SET created_at = now()
WHERE created_at IS NULL;

UPDATE notifications
SET updated_at = COALESCE(updated_at, created_at, now())
WHERE updated_at IS NULL;

ALTER TABLE notifications ALTER COLUMN body SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN message SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN data SET DEFAULT '{}'::jsonb;
ALTER TABLE notifications ALTER COLUMN data SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN channel SET DEFAULT 'push';
ALTER TABLE notifications ALTER COLUMN channel SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN priority SET DEFAULT 'normal';
ALTER TABLE notifications ALTER COLUMN priority SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN is_read SET DEFAULT false;
ALTER TABLE notifications ALTER COLUMN is_read SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN sent_at SET DEFAULT now();
ALTER TABLE notifications ALTER COLUMN sent_at SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN is_deleted SET DEFAULT false;
ALTER TABLE notifications ALTER COLUMN is_deleted SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE notifications ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE notifications ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_created_at ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_deleted_created_at ON notifications(user_id, is_deleted, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications(user_id, is_deleted, read_at);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread_legacy ON notifications(user_id, is_deleted, is_read, read_at);

DROP TRIGGER IF EXISTS trg_notifications_updated_at ON notifications;
CREATE TRIGGER trg_notifications_updated_at
BEFORE UPDATE ON notifications
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();

ALTER TABLE notifications DISABLE ROW LEVEL SECURITY;
