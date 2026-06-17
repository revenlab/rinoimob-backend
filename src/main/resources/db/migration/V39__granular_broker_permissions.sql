DELETE FROM role_permissions rp
USING tenant_roles tr
WHERE rp.role_id = tr.id
  AND tr.is_system = TRUE
  AND LOWER(tr.name) = 'corretor'
  AND rp.permission IN ('leads:read', 'leads:write', 'tasks:read', 'tasks:write');

INSERT INTO role_permissions (role_id, permission)
SELECT tr.id, permission
FROM tenant_roles tr
CROSS JOIN (
    VALUES
        ('leads:read_own'),
        ('leads:write_own'),
        ('tasks:read_own'),
        ('tasks:write_own'),
        ('properties:read'),
        ('whatsapp:read'),
        ('whatsapp:write')
) AS perms(permission)
WHERE tr.is_system = TRUE
  AND LOWER(tr.name) = 'corretor'
ON CONFLICT (role_id, permission) DO NOTHING;
