ALTER TABLE whatsapp_instances
ADD COLUMN assigned_to_user_id UUID;

ALTER TABLE whatsapp_instances
ADD CONSTRAINT fk_whatsapp_instances_assigned_to_user
  FOREIGN KEY (assigned_to_user_id) REFERENCES users(id);

CREATE INDEX idx_whatsapp_instances_assigned_to_user
ON whatsapp_instances(assigned_to_user_id);
